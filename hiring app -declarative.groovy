pipeline {
    agent any

    tools {
        maven "MVN_HOME"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/pmohd6065-ux/hiring-app.git'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean install -f pom.xml'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh 'mvn sonar:sonar'
                }
            }
        }

        stage('Upload to Nexus') {
            steps {
                script {
                    nexusArtifactUploader(
                        artifacts: [[
                            artifactId: 'hiring',
                            classifier: '',
                            file: "target/hiring.war",
                            type: 'war'
                        ]],
                        credentialsId: 'nexus',
                        groupId: 'in.javahome',
                        nexusUrl: '16.16.25.136:8081',
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        repository: 'hiring-app',
                        version: "3.0-SNAPSHOT"
                    )
                }
            }
        }

        stage('Deploy to Tomcat') {
            steps {
                script {
                    deploy adapters: [
                        tomcat9(
                            credentialsId: 'tomcat1',
                            path: '',
                            url: 'http://56.228.15.251:8080/manager'
                        )
                    ],
                    war: '**/target/hiring.war'
                }
            }
        }

        stage('Slack Notification') {
            steps {
                slackSend(
                    channel: '#jenkins-integration',
                    message: "✅ Declarative pipeline for hiring app successfully completed "
                )
            }
        }
    }
}
