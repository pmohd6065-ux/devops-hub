pipeline {
    agent any

    parameters {
        string(name: 'BRANCH', defaultValue: 'master', description: 'Git branch to build')
        string(name: 'MVN_HOME', defaultValue: 'MVN_HOME', description: 'Maven tool name')
        stringParam(name: 'RUN_SONAR', defaultValue: true, description: 'Run SonarQube analysis')
        stringParam(name: 'UPLOAD_TO_NEXUS', defaultValue: true, description: 'Upload WAR to Nexus')
        stringParam(name: 'DEPLOY_TO_TOMCAT', defaultValue: true, description: 'Deploy WAR to Tomcat')
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: "${params.BRANCH}",
                    url: 'https://github.com/pmohd6065-ux/sabear_simplecutomerapp.git'
            }
        }

        stage('Build') {
            steps {
                script {
                    def mvnHome = tool "${params.MVN_HOME}"
                    sh "${mvnHome}/bin/mvn clean package -f pom.xml"
                }
            }
        }

        stage('SonarQube Analysis') {
            when { expression { return params.RUN_SONAR } }
            steps {
                script {
                    def mvnHome = tool "${params.MVN_HOME}"
                    withSonarQubeEnv('SonarQube') {
                        sh "${mvnHome}/bin/mvn org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar"
                    }
                }
            }
        }

        stage('Upload to Nexus') {
            when { expression { return params.UPLOAD_TO_NEXUS } }
            steps {
                nexusArtifactUploader(
                    artifacts: [[
                        artifactId: 'SimpleCustomerApp',
                        classifier: '',
                        file: "/var/lib/jenkins/workspace/parameter/target/SimpleCustomerApp-${env.BUILD_NUMBER}-SNAPSHOT.war",
                        type: 'war'
                    ]],
                    credentialsId: 'nexus-server',
                    groupId: 'com.javatpoint',
                    nexusUrl: '13.51.159.64:8081',
                    nexusVersion: 'nexus3',
                    protocol: 'http',
                    repository: 'spring3',
                    version: "${env.BUILD_NUMBER}-SNAPSHOT"
                )
            }
        }

        stage('Deploy to Tomcat') {
            when { expression { return params.DEPLOY_TO_TOMCAT } }
            steps {
                deploy adapters: [tomcat9(
                    credentialsId: 'tomcat',
                    path: '',
                    url: 'http://13.61.6.206:8080'
                )],
                contextPath: null,
                war: '**/*.war'
            }
        }

        stage('Slack Notification') {
            steps {
                slackSend channel: 'jenkins-integration',
                    message: "Build completed successfully!",
                    tokenCredentialId: 'slack'
            }
        }
    }
}
