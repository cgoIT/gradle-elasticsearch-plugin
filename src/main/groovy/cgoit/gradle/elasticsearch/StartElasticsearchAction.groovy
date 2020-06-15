package cgoit.gradle.elasticsearch

import com.kstruct.gethostname4j.Hostname
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.internal.impldep.org.apache.commons.lang.BooleanUtils

import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.CYAN
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_HOST
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_PORT
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_SCHEME
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_TRANSPORT_PORT
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_VERSION
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_VERSION
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.NORMAL
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.RED
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.YELLOW
import static org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS
import static org.apache.tools.ant.taskdefs.condition.Os.isFamily

class StartElasticsearchAction {

    @Input
    @Optional
    String elasticsearchVersion

    @Input
    @Optional
    String httpScheme

    @Input
    @Optional
    String httpHost

    @Input
    @Optional
    Integer httpPort

    @Input
    @Optional
    Integer transportPort

    @Input
    @Optional
    File toolsDir

    @Input
    @Optional
    File dataDir

    @Input
    @Optional
    File logsDir

    @Input
    @Optional
    Boolean forceShutdownBeforeStart = Boolean.FALSE

    private Project project

    private AntBuilder ant

    StartElasticsearchAction(Project project) {
        this.project = project
        this.ant = project.ant
    }

    void execute() {
        File toolsDir = toolsDir ?: new File("$project.rootDir/gradle/tools")
        elasticsearchVersion = elasticsearchVersion ?: DEFAULT_ELASTICSEARCH_VERSION
        httpScheme = httpScheme ?: DEFAULT_ELASTICSEARCH_SCHEME
        httpHost = httpHost ?: DEFAULT_ELASTICSEARCH_HOST
        httpPort = httpPort ?: DEFAULT_ELASTICSEARCH_PORT
        transportPort = transportPort ?: DEFAULT_ELASTICSEARCH_TRANSPORT_PORT
        dataDir = dataDir ?: new File("$project.buildDir/elastic")
        File tmpDir = new File("$project.buildDir/elastic/tmp")
        logsDir = logsDir ?: new File("$dataDir/logs")
        File pidFile = new File(toolsDir, 'elastic/elastic.pid')
        File home = new File(toolsDir, "elastic")

        ElasticsearchActions elastic = new ElasticsearchActions(project, toolsDir,
                elasticsearchVersion, httpScheme, httpHost, httpPort, pidFile)

        elastic.install()

        if (elastic.isRunning()) {
            if (BooleanUtils.isFalse(forceShutdownBeforeStart)) {
                println "${YELLOW}* elastic:$NORMAL ElasticSearch seems to be running at pid ${pidFile.text}"
                println "${YELLOW}* elastic:$NORMAL please check $pidFile"
                return
            }

            String pid = elastic.getPid()
            println "${CYAN}* elastic:$NORMAL ElasticSearch seems to be running at pid ${pid} and 'forceShutdownBeforeStart=true'"
            elastic.stopRunning()
        }

        println "${CYAN}* elastic:$NORMAL starting ElasticSearch at $elastic.home using http port $httpPort and tcp transport port $transportPort"
        println "${CYAN}* elastic:$NORMAL ElasticSearch data directory: $dataDir"
        println "${CYAN}* elastic:$NORMAL ElasticSearch logs directory: $logsDir"
        println "${CYAN}* elastic:$NORMAL ElasticSearch tmp directory: $tmpDir"

        ant.delete(failonerror: true, dir: dataDir)
        ant.delete(failonerror: true, dir: logsDir)
        ant.delete(failonerror: true, dir: tmpDir)
        logsDir.mkdirs()
        dataDir.mkdirs()
        tmpDir.mkdirs()

        def optPrefix = Integer.valueOf(elastic.version.split("\\.")[0]) >= 5 ? "-E" : "-Des."
        File esScript = new File("${elastic.home}/bin/elasticsearch${isFamily(FAMILY_WINDOWS) ? '.bat' : ''}")
        def command = [
                esScript.absolutePath,
                "${optPrefix}http.port=$httpPort",
                "${optPrefix}transport.tcp.port=$transportPort",
                "${optPrefix}path.data=$dataDir",
                "${optPrefix}path.repo=$dataDir/repo",
                "${optPrefix}path.logs=$logsDir",
        ]

        def mlModule = new File("$home/modules/x-pack-ml")
        if (mlModule.exists()) {
            command +=  "${optPrefix}xpack.ml.enabled=false"
        }

        println "${CYAN}* elastic:$NORMAL start ElasticSearch with parameters: ${command.toListString()}"

        if (!isFamily(FAMILY_WINDOWS)) {
            command += [
                    "-p${pidFile}"
            ]
        }

        def esEnv = [
                "JAVA_HOME=${System.properties['java.home']}",
                "ES_HOME=$elastic.home"
        ]

        if (isFamily(FAMILY_WINDOWS)) {
            esEnv += [
                    "COMPUTERNAME=${Hostname.getHostname()}",
                    "ES_TMPDIR=${tmpDir.absolutePath}"
            ]
        }

        if (Integer.valueOf(elastic.version.split("\\.")[0]) >= 5) {
            esEnv += [
                    "ES_JAVA_OPTS=-Xms128m -Xmx512m"
            ]
        } else {
            esEnv += [
                    "ES_MAX_MEM=512m",
                    "ES_MIN_MEM=128m"
            ]
        }

        Process p = command.execute(esEnv, elastic.home)

        def out = new StringBuilder()
        p.consumeProcessOutput(out, out)

        println "${CYAN}* elastic:$NORMAL waiting for ElasticSearch to start"
        boolean running = elastic.isRunning(120)

        println out

        if (running) {
            if (isFamily(FAMILY_WINDOWS)) {
                if (pidFile.exists()) {
                    ant.delete(failonerror: true, file: pidFile)
                    pidFile = new File(elastic.home, 'elastic.pid')
                }

                // save pid to file
                pidFile.withWriter {
                    it.write(elastic.getPid())
                }
            }
            println "${CYAN}* elastic:$NORMAL ElasticSearch is now up"
        } else {
            println "${RED}* elastic:$NORMAL could not start ElasticSearch"
            throw new RuntimeException("failed to start ElasticSearch")
        }
    }
}
