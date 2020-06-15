package cgoit.gradle.elasticsearch

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_HOST
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_PORT
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_SCHEME
import static cgoit.gradle.elasticsearch.ElasticsearchPlugin.DEFAULT_ELASTICSEARCH_VERSION

class StopElasticsearchAction {

    @Input
    @Optional
    private String httpScheme

    @Input
    @Optional
    private String httpHost

    @Input
    @Optional
    private Integer httpPort

    @Input
    @Optional
    File toolsDir

    @Input
    @Optional
    String elasticVersion

    private AntBuilder ant
    private Project project

    StopElasticsearchAction(Project project) {
        this.project = project
        this.ant = project.ant
    }

    void execute() {
        File toolsDir = toolsDir ?: new File("$project.rootDir/gradle/tools")
        def pidFile = new File(toolsDir, 'elastic/elastic.pid')
        httpScheme = httpScheme ?: DEFAULT_ELASTICSEARCH_SCHEME
        httpHost = httpHost ?: DEFAULT_ELASTICSEARCH_HOST
        httpPort = httpPort ?: DEFAULT_ELASTICSEARCH_PORT
        String mirrorUrl = null;

        ElasticsearchActions elastic = new ElasticsearchActions(project, toolsDir,
                elasticVersion ?: DEFAULT_ELASTICSEARCH_VERSION,
                httpScheme, httpHost, httpPort, pidFile, mirrorUrl)

        if (elastic.isRunning()) {
            elastic.stopRunning()
        }
    }
}
