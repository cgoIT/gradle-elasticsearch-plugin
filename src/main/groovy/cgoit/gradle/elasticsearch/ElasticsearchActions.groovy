package cgoit.gradle.elasticsearch

import de.undercouch.gradle.tasks.download.DownloadAction
import groovy.json.JsonSlurper
import org.gradle.api.Project

import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.CYAN
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.NORMAL
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.RED
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.YELLOW
import static org.apache.http.client.fluent.Executor.newInstance
import static org.apache.http.client.fluent.Request.Post
import static org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import static org.apache.tools.ant.taskdefs.condition.Os.isFamily

class ElasticsearchActions {
    String version
    File toolsDir
    Project project
    AntBuilder ant
    File home
    String httpScheme
    String httpHost
    Integer httpPort
    File pidFile

    ElasticsearchActions(Project project, File toolsDir, String version,
            String httpScheme, String httpHost, Integer httpPort, File pidFile) {
        this.project = project
        this.toolsDir = toolsDir
        this.version = version
        this.ant = project.ant
        home = new File("$toolsDir/elastic")
        this.httpScheme = httpScheme
        this.httpHost = httpHost
        this.httpPort = httpPort
        this.pidFile = pidFile
    }

    boolean isRunning(maxWait = 10) {
        def url = "${httpScheme}://${httpHost}:$httpPort"
        def wait = 0
        def running = false
        while (!running && wait <= maxWait) {
            try {
                url.toURL().openConnection().with {
                    connectTimeout = 2000
                    if (responseCode == 200) {
                        running = true
                    }
                    disconnect()
                }
            } catch (e) { }
            sleep(2000)
            wait += 2
        }

        running
    }

    boolean waitForShutdown(maxWait = 10) {
        def url = "${httpScheme}://${httpHost}:$httpPort"
        def wait = 0
        def shutdown = false
        while (!shutdown && wait <= maxWait) {
            try {
                url.toURL().openConnection().with {
                    connectTimeout = 2000
                    println "${CYAN}* elastic:$NORMAL wait ${maxWait - wait} more seconds for shutdown, responseCode=${responseCode}"
                    disconnect()
                }
            } catch (e) {
                shutdown = true
            }
            sleep(2000)
            wait += 2
        }

        shutdown
    }

    String getPid() {
        String pid
        def status
        try {
            status = new JsonSlurper().parse(new URL("${httpScheme}://${httpHost}:${httpPort}/_nodes/process"))
        } catch (ConnectException e) {
        }

        if (status) {
            def nodes = status?."nodes"
            if (nodes) {
                nodes.find { k, v ->
                    pid = v."process"?."id"
                    if (pid) {
                        return true
                    }
                }
            }
        }

        pid
    }

    boolean stopRunning() {
        println "${CYAN}* elastic:$NORMAL stopping Elasticsearch"

        try {
            if (Integer.valueOf(version.split("\\.")[0]) >= 2) {
                def elasticPid
                if (pidFile.exists()) {
                    elasticPid = pidFile.text
                    ant.delete(failonerror: true, file: pidFile)
                } else {
                    elasticPid = getPid()
                }

                if (!elasticPid) {
                    println "${RED}* elastic:$NORMAL could not get pid of running Elasticsearch!"
                    println "${RED}* elastic:$NORMAL could not stop Elasticsearch, please check manually!"
                    return false
                }
                println "${CYAN}* elastic:$NORMAL going to kill pid $elasticPid"

                if (isFamily(FAMILY_WINDOWS)) {
                    "taskkill /F /PID $elasticPid".execute()
                } else {
                    "kill $elasticPid".execute()
                }
            } else {
                newInstance().
                        execute(Post("${httpScheme}://${httpHost}:${httpPort}/_shutdown"))
            }

            println "${CYAN}* elastic:$NORMAL waiting for Elasticsearch to shutdown"
            boolean shutdown = waitForShutdown(120)

            if (!shutdown) {
                println "${RED}* elastic:$NORMAL could not stop Elasticsearch"
                return false
            }

            println "${CYAN}* elastic:$NORMAL Elasticsearch is now down"
        } catch (ConnectException e) {
            println "${CYAN}* elastic:$YELLOW warning - unable to stop elastic on http port ${httpPort}, ${e.message}$NORMAL"
            return false
        }
        return true
    }

    boolean isInstalled() {
        if (!new File("$home/bin/elasticsearch").exists()) {
            return false
        }

        boolean desiredVersion = isDesiredVersion()

        if (!desiredVersion) {
            // this is not the desired version, then we also need to delete the home directory
            ant.delete(dir: home)

            return false
        }

        return true
    }

    boolean isDesiredVersion() {
        println "${CYAN}* elastic:$NORMAL check if existing version is $version"

        def versionFile = new File("$home/version.txt")
        if (!versionFile.exists()) {
            return false
        }

        def detectedVersion = versionFile.text

        println "${CYAN}* elastic:$NORMAL detected version: $detectedVersion"

        return detectedVersion.trim() == version
    }

    void install() {
        if (isInstalled()) {
            println "${CYAN}* elastic:$NORMAL Elasticsearch version $version detected at $home"
            return
        }

        println "${CYAN}* elastic:$NORMAL installing elastic version $version"

        def majorVersion = Integer.valueOf(version.split("\\.")[0])

        String linuxUrl
        String winUrl

        switch (majorVersion) {
            case 0:
            case 1:
                linuxUrl = "https://download.elastic.co/elasticsearch/elasticsearch/elasticsearch-${version}.tar.gz"
                winUrl = "https://download.elastic.co/elasticsearch/elasticsearch/elasticsearch-${version}.zip"
                break

            case 2:
                linuxUrl = "https://download.elasticsearch.org/elasticsearch/release/org/elasticsearch/distribution/tar/elasticsearch/${version}/elasticsearch-${version}.tar.gz"
                winUrl = "https://download.elasticsearch.org/elasticsearch/release/org/elasticsearch/distribution/zip/elasticsearch/${version}/elasticsearch-${version}.zip"
                break

        // there are no versions 3 and 4

            case 7:
                linuxUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}-linux-x86_64.tar.gz"
                winUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}-windows-x86_64.zip"
                break


            default: // catches version 5 and up
                linuxUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}.tar.gz"
                winUrl = "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${version}.zip"
                break
        }

        String elasticPackage = isFamily(FAMILY_WINDOWS) ? winUrl : linuxUrl
        File elasticFile = new File("$toolsDir/elastic-${version}.${isFamily(FAMILY_WINDOWS) ? 'zip' : 'tar.gz'}")
        File elasticFilePart = new File(
                "$toolsDir/elastic-${version}.${isFamily(FAMILY_WINDOWS) ? 'zip' : 'tar.gz'}.part")

        ant.delete(quiet: true) {
            fileset(dir: toolsDir) {
                include(name: "**/*.part")
            }
        }

        DownloadAction elasticDownload = new DownloadAction(project)
        elasticDownload.dest(elasticFilePart)
        elasticDownload.src(elasticPackage)
        elasticDownload.onlyIfNewer(true)
        elasticDownload.execute()

        ant.rename(src: elasticFilePart, dest: elasticFile, replace: true)

        ant.delete(dir: home, quiet: true)
        home.mkdirs()

        if (isFamily(FAMILY_WINDOWS)) {
            ant.unzip(src: elasticFile, dest: "$home") {
                cutdirsmapper(dirs: 1)
            }
        } else {
            ant.untar(src: elasticFile, dest: "$home", compression: "gzip") {
                cutdirsmapper(dirs: 1)
            }
            ant.chmod(file: new File("$home/bin/elasticsearch"), perm: "+x")
            ant.chmod(file: new File("$home/bin/plugin"), perm: "+x")
            ant.chmod(file: new File("$home/modules/x-pack-ml/platform/linux-x86_64/bin/controller"), perm: "+x")
        }

        new File("$home/version.txt") << "$version"
    }
}
