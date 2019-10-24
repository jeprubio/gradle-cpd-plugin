package de.aaschmid.gradle.plugins.cpd.test

import spock.lang.Issue

import static de.aaschmid.gradle.plugins.cpd.test.Lang.*
import static org.gradle.testkit.runner.TaskOutcome.*

class CpdAcceptanceTest extends IntegrationBaseSpec {

    // TODO Test incremental build feature? how?

    def "Cpd will be skipped if no source is set"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                include '**/*.java'
                exclude '**/*1.java'
                exclude '**/*z.java'
                //source
            }
        """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == NO_SOURCE
        result.output.contains("BUILD SUCCESSFUL")
        !result.output.contains('WARNING: Due to the absence of \'LifecycleBasePlugin\' on root project')
    }

    // TODO use pmd dependency if pmd plugin applied?

    def "Cpd fails if no report is enabled"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                reports{
                    csv.enabled = false
                    text.enabled = false
                    xml.enabled = false
                }
                source = '.'
            }
        """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output.contains("Task 'cpdCheck' requires at least one enabled report.")

        !file('build/reports/cpdCheck.csv').exists()
    }

    def "Cpd will produce empty 'cpdCheck.xml' on non-duplicate 'java' source"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpd{
                encoding = 'ISO-8859-1'
                minimumTokenCount = 10
            }
            cpdCheck.source = ${testPath(JAVA, 'de/aaschmid/foo')}
        """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = testProjectDir.getRoot().toPath().resolve('build/reports/cpd/cpdCheck.xml').toFile()
        report.exists()
        report.text =~ /encoding="ISO-8859-1"/
        report.text =~ /<pmd-cpd\/>/
    }

    @Issue("https://github.com/aaschmid/gradle-cpd-plugin/issues/38")
    def "Cpd should not produce 'cpdCheck.txt' on duplicate 'java' comments in source"() {
        given:
        buildFileWithPluginAndRepos([ 'java']) << """
            cpdCheck{
                reports{
                    xml.enabled = false
                    text.enabled = true
                }
                source = ${testPath(JAVA, 'de/aaschmid/test', 'de/aaschmid/duplicate')}
            }
        """.stripIndent()

        print(buildFile.text)

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.text')
        report.exists()
        report.text.empty
    }

    @Issue("https://github.com/aaschmid/gradle-cpd-plugin/issues/38")
    def "Cpd should not produce 'cpdCheck.txt' on duplicate 'kotlin' comments in source"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                language = 'kotlin'
                minimumTokenCount = 10
                reports{
                    xml.enabled = false
                    text.enabled = true
                }
                source = ${testPath(KOTLIN, 'de/aaschmid/test')}
            }
        """.stripIndent()

        println(buildFile.text)

        when:
        def result = run("cpdCheck")

        then:
        println(result.output)
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.text')
        report.exists()
        report.text.empty
    }

    def "Cpd should fail and produce 'cpdCheck.csv' with one warning on duplicate 'java' source"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                minimumTokenCount = 15
                reports{
                    csv.enabled = true
                    xml.enabled = false
                }
                source = ${testPath(JAVA, 'de/aaschmid/clazz')}
            }
        """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output =~ /CPD found duplicate code\. See the report at file:\/\/.*\/cpdCheck.csv/

        def report = file('build/reports/cpd/cpdCheck.csv')
        report.exists()
        report.text =~ /4,15,2,[79],.*Clazz[12]\.java,[79],.*Clazz[12]\.java/
    }

    def "Cpd should not fail if 'ignoreFailures' and produce 'cpdCheck.csv' with one warning on duplicate 'java' source"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreFailures = true
                minimumTokenCount = 15
                reports{
                    csv.enabled = true
                    xml.enabled = false
                }
                source files(${testPath(JAVA, 'de/aaschmid/clazz')})
            }
        """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.csv')
        report.exists()
        report.text =~ /4,15,2,[79],.*Clazz[12]\.java,[79],.*Clazz[12]\.java/
    }

    def "Cpd should create and fill all enabled reports"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck {
                language = 'kotlin'
                minimumTokenCount = 5
                reports {
                    csv.enabled = false
                    text.enabled = true
                    xml.enabled = true
                }
                source = ${testPath(KOTLIN, '.')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")

        def csv = file('build/reports/cpd/cpdCheck.csv')
        def txt = file('build/reports/cpd/cpdCheck.text')
        def xml = file('build/reports/cpd/cpdCheck.xml')

        !csv.exists()

        txt.exists()
        txt.text.contains("Found a 4 line (9 tokens) duplication in the following files:")
        txt.text.contains("Found a 2 line (8 tokens) duplication in the following files:")

        xml.exists()
        xml.text.contains('<duplication lines="4" tokens="9">')
        xml.text.contains('<duplication lines="2" tokens="8">')
    }

    def "Cpd should fail if not ignoreAnnotations on duplicate annotations"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreAnnotations = false
                minimumTokenCount = 40
                reports{
                    csv.enabled = true
                    xml.enabled = false
                }
                source = ${testPath(JAVA, 'de/aaschmid/annotation')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output =~ /CPD found duplicate code\. See the report at file:\/\/.*\/cpdCheck.csv/

        def report = file('build/reports/cpd/cpdCheck.csv')
        report.exists()
        // locally Person.java comes before Employee, on travis-ci is Employee first => make it irrelevant
        report.text =~ /8,53,2,6,.*(Person|Employee)\.java,6,.*(Person|Employee)\.java/
        report.text =~ /14,45,2,13,.*(Person|Employee)\.java,13,.*(Person|Employee)\.java/
    }

    def "Cpd should not fail if ignoreAnnotations on duplicate annotations"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreAnnotations = true
                minimumTokenCount = 40
                source = ${testPath(JAVA, 'de/aaschmid/annotation')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.xml')
        report.exists()
        // TODO do better?
        report.text =~ /<pmd-cpd\/>/
    }

    def "Cpd should fail if ignoreIdentifiers on different identifiers"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreIdentifiers = true
                minimumTokenCount = 15
                reports{
                    csv.enabled = true
                    xml.enabled = false
                }
                source = ${testPath(JAVA, 'de/aaschmid/identifier')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output =~ /CPD found duplicate code\. See the report at file:\/\/.*\/cpdCheck.csv/

        def report = file('build/reports/cpd/cpdCheck.csv')
        report.exists()
        report.text =~ /6,19,2,3,.*Identifier[12]\.java,3,.*Identifier[12]\.java/
    }

    def "Cpd should not fail if not ignoreIdentifiers on different annotations"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreIdentifiers = false
                minimumTokenCount = 15
                source = ${testPath(JAVA, 'de/aaschmid/identifier')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.xml') // TODO file exists always; same as for other tools?
        report.exists()
        // TODO do better?
        report.text =~ /<pmd-cpd\/>/
    }

    def "Cpd should fail if ignoreLiterals on different literals"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreLiterals = true
                minimumTokenCount = 20
                reports{
                    csv.enabled = true
                    xml.enabled = false
                }
                source = ${testPath(JAVA, 'de/aaschmid/literal')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output =~ /CPD found duplicate code\. See the report at file:\/\/.*\/cpdCheck.csv/

        def report = file('build/reports/cpd/cpdCheck.csv')
        report.exists()
        report.text =~ /9,27,2,5,.*Literal[12]\.java,5,.*Literal[12]\.java/
    }

    def "Cpd should not fail if not ignoreLiterals on different literals"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                ignoreLiterals = false
                minimumTokenCount = 20
                source = ${testPath(JAVA, 'de/aaschmid/literal')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.xml') // TODO file exists always; same as for other tools?
        report.exists()
        // TODO do better?
        report.text =~ /<pmd-cpd\/>/
    }

    def "Cpd should fail if not skipDuplicateFiles on duplicate files"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                minimumTokenCount = 5
                reports{
                    csv.enabled = true
                    xml.enabled = false
                }
                skipDuplicateFiles = false
                source = ${testPath(JAVA, 'de/aaschmid/duplicate', 'de/aaschmid/test')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output =~ /CPD found duplicate code\. See the report at file:\/\/.*\/cpdCheck.csv/

        def report = file('build/reports/cpd/cpdCheck.csv')
        report.exists()
        report.text =~ /6,15,2,20,.*(duplicate|test)\/Test\.java,20,.*(duplicate|test)\/Test\.java/
    }

    def "Cpd should not fail if skipDuplicateFiles on duplicate files"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                minimumTokenCount = 5
                skipDuplicateFiles = true
                source = ${testPath(JAVA, 'de/aaschmid/duplicate', 'de/aaschmid/test')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.xml') // TODO file exists always; same as for other tools?
        report.exists()
        // TODO do better?
        report.text =~ /<pmd-cpd\/>/
    }


    def "Cpd should fail if not skipLexicalErrors on files containing lexical errors"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                skipLexicalErrors = false
                source = ${testPath(JAVA, 'de/aaschmid/lexical')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == FAILED
        result.output.contains("BUILD FAILED")
        result.output =~ /Lexical error in file .*Error.java at/

        def report = file('build/reports/cpd/cpdCheck.csv')
        !report.exists()
    }

    def "Cpd should not fail if skipLexicalErrors on files containing lexical errors"() {
        given:
        buildFileWithPluginAndRepos() << """
            cpdCheck{
                skipLexicalErrors = true
                source = ${testPath(JAVA, 'de/aaschmid/lexical')}
            }
            """.stripIndent()

        when:
        def result = run("cpdCheck")

        then:
        result.task(':cpdCheck').outcome == SUCCESS
        result.output.contains("BUILD SUCCESSFUL")

        def report = file('build/reports/cpd/cpdCheck.xml') // TODO file exists always; same as for other tools?
        report.exists()
        // TODO do better?
        report.text =~ /<pmd-cpd\/>/
    }
}
