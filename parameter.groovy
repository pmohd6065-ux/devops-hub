// Jenkinsfile (Scripted Pipeline — Parameterized)

properties([
  parameters([
    // Source control
    string(name: 'GIT_URL',   defaultValue: 'https://github.com/pmohd6065-ux/sabear_simplecutomerapp.git', description: 'Git repository URL'),
    string(name: 'BRANCH',    defaultValue: 'master', description: 'Git branch to build'),
    // Tooling
    string(name: 'MVN_TOOL',  defaultValue: 'MVN_HOME', description: 'Name of Maven tool from Jenkins Global Tools'),
    // SonarQube
    string(name: 'SONARQUBE_SERVER', defaultValue: 'SonarQube', description: 'SonarQube server name (Manage Jenkins → Configure System)'),
    booleanParam(name: 'RUN_SONAR', defaultValue: true, description: 'Run SonarQube analysis'),
    // Artifact coordinates
    string(name: 'GROUP_ID',     defaultValue: 'com.javatpoint', description: 'Maven Group ID for upload'),
    string(name: 'ARTIFACT_ID',  defaultValue: 'SimpleCustomerApp', description: 'Maven Artifact ID'),
    string(name: 'VERSION',      defaultValue: '', description: 'Version for Nexus upload (leave empty to use BUILD_NUMBER-SNAPSHOT)'),
    // Nexus upload
    string(name: 'NEXUS_URL',          defaultValue: '13.51.159.64:8081', description: 'Host:port of Nexus'),
    choice(name: 'NEXUS_PROTOCOL',     choices: ['http','https'], description: 'Protocol for Nexus'),
    string(name: 'NEXUS_REPOSITORY',   defaultValue: 'feature', description: 'Nexus repository (hosted)'),
    string(name: 'NEXUS_CREDENTIALS',  defaultValue: 'nexus-server', description: 'Credentials ID for Nexus'),
    booleanParam(name: 'UPLOAD_TO_NEXUS', defaultValue: true, description: 'Upload built artifact to Nexus'),
    // Tomcat deploy
    string(name: 'TOMCAT_URL',          defaultValue: 'http://13.61.6.206:8080', description: 'Tomcat Manager base URL'),
    string(name: 'TOMCAT_CREDENTIALS',  defaultValue: 'tomcat', description: 'Credentials ID for Tomcat'),
    booleanParam(name: 'DEPLOY_TO_TOMCAT', defaultValue: true, description: 'Deploy to Tomcat after build'),
    // Slack
    booleanParam(name: 'SEND_SLACK', defaultValue: true, description: 'Send Slack notification at the end'),
    string(name: 'SLACK_CHANNEL', defaultValue: 'jenkins-integration', description: 'Slack channel'),
    string(name: 'SLACK_CREDENTIAL', defaultValue: 'slack', description: 'Slack token Credential ID')
  ])
])

timestamps {
  ansiColor('xterm') {
    node {
      // Resolve tools & derived values
      def mvnHome = tool params.MVN_TOOL
      def version = (params.VERSION?.trim()) ? params.VERSION.trim() : "${env.BUILD_NUMBER}-SNAPSHOT"
      def warName = "${params.ARTIFACT_ID}-${version}.war"
      def warPath = "target/${warName}"

      stage('Checkout') {
        checkout([
          $class: 'GitSCM',
          branches: [[name: "*/${params.BRANCH}"]],
          userRemoteConfigs: [[url: params.GIT_URL]]
        ])
      }

      stage('Build') {
        sh "${mvnHome}/bin/mvn -V -B clean package -f pom.xml"
      }

      if (params.RUN_SONAR) {
        stage('SonarQube Analysis') {
          withSonarQubeEnv(params.SONARQUBE_SERVER) {
            sh "${mvnHome}/bin/mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar"
          }
        }
      }

      if (params.UPLOAD_TO_NEXUS) {
        stage('Upload to Nexus') {
          // Optional sanity check to help catch mismatched POM version vs expected VERSION
          sh "ls -l target/*.war || true"
          if (!fileExists(warPath)) {
            error "WAR not found at ${warPath}. Ensure your POM produces ${warName} or adjust VERSION/ARTIFACT_ID."
          }

          nexusArtifactUploader(
            artifacts: [[
              artifactId: params.ARTIFACT_ID,
              classifier: '',
              file: warPath,
              type: 'war'
            ]],
            credentialsId: params.NEXUS_CREDENTIALS,
            groupId: params.GROUP_ID,
            nexusUrl: params.NEXUS_URL,
            nexusVersion: 'nexus3',
            protocol: params.NEXUS_PROTOCOL,
            repository: params.NEXUS_REPOSITORY,
            version: version
          )
        }
      }

      if (params.DEPLOY_TO_TOMCAT) {
        stage('Tomcat Deploy') {
          deploy adapters: [tomcat9(
            alternativeDeploymentContext: '',
            credentialsId: params.TOMCAT_CREDENTIALS,
            path: '',
            url: params.TOMCAT_URL
          )],
          contextPath: null,
          war: '**/*.war'  // Deploy the WAR built in /target
        }
      }

      if (params.SEND_SLACK) {
        stage('Slack Notification') {
          slackSend(
            channel: params.SLACK_CHANNEL,
            message: "Completed scripted pipeline successfully. Job: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            tokenCredentialId: params.SLACK_CREDENTIAL
          )
        }
      }
    }
  }
}
