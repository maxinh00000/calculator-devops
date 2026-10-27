// Declarative CI/CD pipeline for the Calculator REST API
// Checkout -> Build -> Test -> Docker Build -> Push -> Deploy (+ email notification)
//
// One-time Jenkins setup (see docs/SETUP_GUIDE.md):
//   * Credentials: "dockerhub-creds" (Username with password / access token)
//   * Email Extension plugin + SMTP configured in Manage Jenkins > System
//   * The jenkins user is in the "docker" group

pipeline {
    agent any

    environment {
        DOCKERHUB_USER = 'maxinh0'                 // <-- your Docker Hub username (lowercase)
        IMAGE_NAME     = "${DOCKERHUB_USER}/calculator"
        IMAGE_TAG      = "${env.BUILD_NUMBER}"
        NOTIFY_EMAIL   = 'thrivedhundi5@gmail.com'      // <-- where build status mails go
        APP_URL        = 'http://localhost:8085'
    }

    options {
        timestamps()
        disableConcurrentBuilds()                     // two deploys never race each other
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers {
        githubPush()                                  // GitHub webhook (needs a public Jenkins URL, e.g. via ngrok)
        pollSCM('H/2 * * * *')                        // fallback: poll GitHub every ~2 minutes
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --pretty="format:%h %an: %s"'
            }
        }

        stage('Build') {
            steps {
                sh '''
                export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
                export PATH="$JAVA_HOME/bin:$PATH"

                echo "Java version:"
                java -version

                echo "Java compiler version:"
                javac -version

                echo "Maven Java configuration:"
                mvn -version

                mvn -B clean package -DskipTests
            '''
        }
}

        stage('Test') {
            steps {
                // mvn exits non-zero on any failing test -> stage (and build) fails
                sh 'mvn -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build \
                      -t ${IMAGE_NAME}:${IMAGE_TAG} \
                      -t ${IMAGE_NAME}:latest \
                      .
                '''
            }
        }

        stage('Push') {
            steps {
                // Credentials come from the Jenkins store; never written to the repo or the log
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                                                  usernameVariable: 'DH_USER',
                                                  passwordVariable: 'DH_PASS')]) {
                    sh '''
                        echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                sh 'bash scripts/deploy.sh'
                sh "bash scripts/smoke-test.sh ${APP_URL}"
            }
        }
    }

    post {
        success {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                body: """<p>Build <b>#${env.BUILD_NUMBER}</b> of <b>${env.JOB_NAME}</b> succeeded.</p>
                         <p>Image: <code>${IMAGE_NAME}:${IMAGE_TAG}</code> pushed and deployed.</p>
                         <p>Console: <a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a></p>"""
            )
        }
        failure {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                mimeType: 'text/html',
                attachLog: true,
                body: """<p>Build <b>#${env.BUILD_NUMBER}</b> of <b>${env.JOB_NAME}</b> <span style="color:red">failed</span>.</p>
                         <p>Console: <a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a> (log attached)</p>"""
            )
        }
        always {
            // Remove dangling layers from old builds so the VM disk does not fill up
            sh 'docker image prune -f || true'
        }
    }
}
