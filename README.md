# gradle-elasticsearch-plugin
An ElasticSearch gradle plugin for starting an ElasticSearch within a gradle build.

[ ![Build Status](https://travis-ci.org/cgoIT/gradle-elasticsearch-plugin.svg?branch=master) ](https://travis-ci.org/amirkibbar/bilberry)
[ ![Download](https://api.bintray.com/packages/cgoit/maven/gradle-elasticsearch-plugin/images/download.svg) ](https://bintray.com/cgoIT/maven/gradle-elasticsearch-plugin/_latestVersion)

# A big thx

A big thx to the author of the original plugin which can be found [here](https://github.com/amirkibbar/bilberry).

# Using

Plugin setup with gradle >= 2.1:

```gradle

    plugins {
        id "cgoit.gradle.elasticsearch" version "0.1.1"
    }
```

Plugin setup with gradle < 2.1:

```gradle

    buildscript {
        repositories {
            jcenter()
            maven { url "http://dl.bintray.com/cgoit/maven" }
        }
        dependencies {
            classpath("cgoit.gradle.elasticsearch:gradle-elasticsearch-plugin:0.1.1")
        }
    }

    apply plugin: 'cgoit.gradle.elasticsearch'
```

# Starting and stopping ElasticSearch during the integration tests

```gradle

    task integrationTests(type: Test) {
      reports {
          html.destination "$buildDir/reports/integration-tests"
      }

      include "**/*IT.*"

      doFirst {
        startElasticsearch {
          elasticsearchVersion = "7.3.0"
          httpScheme = "http"
          httpHost = "localhost"
          httpPort = 9200
          transportPort = 9300
          dataDir = file("$buildDir/elastic")
          logsDir = file("$buildDir/elastic/logs")
          forceShutdownBeforeStart = false
        }
      }
  
      doLast {
        stopElasticsearch {
          httpScheme = "http"
          httpHost = "localhost"
          httpPort = 9200
        }
      }
    }
    
    gradle.taskGraph.afterTask { Task task, TaskState taskState ->
      if (task.name == "integrationTests") {
        stopElasticsearch {
          httpScheme = "http"
          httpHost = "localhost"
          httpPort = 9200
        }
      }
    }

    test {
      include '**/*Test.*'
      exclude '**/*IT.*'
    }
```

The above example shows a task called integrationTests which runs all the tests in the project with the IT suffix. The
reports for these tests are placed in the buildDir/reports/integration-tests directory - just to separate them from
regular tests. But the important part here is in the doFirst and doLast. 

In the doFirst ElasticSearch is started. All the values in the example above are the default values, so if these values
work for you they can be ommitted:

```gradle

    doFirst {
      startElastic {}
    }
```

In the doLast ElasticSearch is stopped. Note that ElasticSearch is also stopped in the gradle.taskGraph.afterTask 
section - this is to catch any crashes during the integration tests and make sure that ElasticSearch is stopped in the 
build clean-up phase.

If you want to control the machine learning settings, you can add this parameter in the doFirst block. It may be necessary 
as some versions of Elasticsearch may fail to start due to an unknown parameter error.

```gradle

    doFirst {
      ...
      xpackMlEnabled = false
    }
```

Lastly the regular test task is configured to exclude the tests with the IT suffix - we only wanted to run these in the
integration tests phase, not with the regular tests.

# References

- [ElasticSearch](https://www.elastic.co/products/elasticsearch)
