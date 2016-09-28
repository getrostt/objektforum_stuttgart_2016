def mvnImgDocker = 'maven:3.3-jdk-8-alpine'
def gitUrl = 'https://github.com/wildfly/quickstart.git'

stage('Commit') {
    node {
        def img = docker.image(mvnImgDocker)
        img.pull()
        img.inside {
            git url: gitUrl, branch: '10.x'

            sh "cd kitchensink && mvn clean install"
        }
    }
}
