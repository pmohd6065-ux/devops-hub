node {
    // Use Maven tool configured in Jenkins
    def mvnHome = tool name: 'MVN_HOME', type: 'maven'

    stage('Checkout') {
        git branch: 'main', url: 'https://github.com/pmohd6065-ux/hiring-app.git'
    }

    stage('Build') {
        withEnv(["PATH+MAVEN=${mvnHome}/bin"]) {
            sh 'mvn clean install -f pom.xml'
        }
    }

    stage('SonarQube Analysis') {
        withSonarQubeEnv('SonarQube') {
            withEnv(["PATH+MAVEN=${mvnHome}/bin"]) {
                sh 'mvn sonar:sonar'
            }
        }
    }

    stage('Upload to Nexus') {
        nexusArtifactUploader(
            artifacts: [[
                artifactId: 'hiring',
                classifier: '',
                file: 'target/hiring.war',
                type: 'war'
            ]],
            credentialsId: 'nexus',
            groupId: 'in.javahome',
            nexusUrl: '16.16.25.136:8081',
            nexusVersion: 'nexus3',
            protocol: 'http',
            repository: 'hiring-app',
            version: '3.0-SNAPSHOT'
        )
    }

    stage('Deploy to Tomcat') {
        deploy adapters: [
            tomcat9(
                credentialsId: 'tomcat1',
                path: '',
                url: 'http://56.228.15.251:8080/manager/text'
            )
        ],
        war: '**/target/hiring.war'
    }

    stage('Slack Notification') {
        slackSend(
            channel: '#jenkins-integration',
            message: '✅ Scripted pipeline for hiring app successfully completed'
        )
    }
}
