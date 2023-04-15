def call(service, dockerRepoName, imageName) {
    pipeline {
        agent any
        stages {
            stage('Build') {
                steps {
                    sh "pip install -r ./${service}/requirements.txt"
                    sh 'sleep $(shuf -i 1-10 -n 1) && sleep $(shuf -i 1-10 -n 1)'
                }
            }
            stage('Python Lint') {
                steps {
                    sh "cd ./${service} && pylint-fail-under --fail_under 5.0 *.py"
                }
            }
            stage('Security Check') {
                steps {
                    sh """cd ${service} && \
                    pip-audit -r requirements.txt && \
                    bandit --exit-zero -r . && \
                    hadolint --no-fail Dockerfile"""
                }
            }
            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/main' }
                }
                steps {
                    withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                        sh "docker login -u 'tamimhemat' -p '$TOKEN' docker.io"
                        sh """cd ${service} && \
                        docker build -t ${dockerRepoName}:latest --tag tamimhemat/${dockerRepoName}:${imageName} ."""
                        sh "docker push tamimhemat/${dockerRepoName}:${imageName}"
                    }
                }
            }
            stage('Deploy') {
                steps {
                    withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                        sshagent(credentials: ['ssh-key']) {
                            sh """ssh -o StrictHostKeyChecking=no tamim@134.122.34.5 'docker login -u tamimhemat -p $TOKEN docker.io && docker rm -f ${service} && docker pull tamimhemat/${dockerRepoName}:${imageName} && cd ~/microservices/ && docker compose up -d ${service}'"""
                        }
                    }
                }
            }
        }
    }
}
