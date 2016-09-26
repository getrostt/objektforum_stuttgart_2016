import groovy.json.JsonSlurper

def deploy(deploymentFileName) {
    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'wildFlyManagementCredentials', passwordVariable: 'wildflyMgmtPassword', usernameVariable: 'wildflyMgmtUser']]) {
        def hostname = 'localhost'
        def managementPort = '10090'

        def deploymentNameWoPath = determineFileName(deploymentFileName)
        echo "Name: ${deploymentNameWoPath}"

        // undeploy old war if present
        sh "curl -S -H \"content-Type: application/json\" -d '{\"operation\":\"undeploy\", \"address\":[{\"deployment\":\"${deploymentNameWoPath}\"}]}' --digest http://${env.wildflyMgmtUser}:${env.wildflyMgmtPassword}@${hostname}:${managementPort}/management"
        sh "curl -S -H \"content-Type: application/json\" -d '{\"operation\":\"remove\", \"address\":[{\"deployment\":\"${deploymentNameWoPath}\"}]}' --digest http://${env.wildflyMgmtUser}:${env.wildflyMgmtPassword}@${hostname}:${managementPort}/management"

        echo "Deploying ${deploymentFileName} to ${hostname}:${managementPort} ..."

        // details can be found in: http://blog.arungupta.me/deploy-to-wildfly-using-curl-tech-tip-10/
        // step 1: upload archive
        sh "curl -F \"file=@${deploymentFileName}\" --digest http://${env.wildflyMgmtUser}:${env.wildflyMgmtPassword}@${hostname}:${managementPort}/management/add-content > result.txt"

        // step 2: deploy the archive
        // read result from step 1
        def uploadResult = readFile 'result.txt'
        def bytesValue = extractByteValue(uploadResult)
        if (bytesValue != null) {
            sh "curl -S -H \"Content-Type: application/json\" -d '{\"content\":[{\"hash\": {\"BYTES_VALUE\" : \"${bytesValue}\"}}], \"address\": [{\"deployment\":\"${deploymentNameWoPath}\"}], \"operation\":\"add\", \"enabled\":\"true\"}' --digest http://${env.wildflyMgmtUser}:${env.wildflyMgmtPassword}@${hostname}:${managementPort}/management > result2.txt"
            def deployResult = readFile 'result2.txt'
            def failure = hasFailure(deployResult)
            if (failure != null) {
                error "Deployment of ${deploymentNameWoPath} failed with error: ${failure}"
            }
        } else {
            // fail build as deployment was not successfull
            error "Upload of ${deploymentFileName} failed"
        }
    }
}

@NonCPS
def determineFileName(path) {
    def idx = path.lastIndexOf('/')
    return path.drop(idx+1)
}

@NonCPS
def extractByteValue(uploadResult) {
    // parse JSON
    def jsonSlurper = new JsonSlurper()
    def object = jsonSlurper.parseText(uploadResult)
    def result = null

    // check that upload was successfull
    if (object.outcome == 'success') {
        result = object.result.BYTES_VALUE
    }
    return result
}

@NonCPS
def hasFailure(result) {
    def jsonSlurper = new JsonSlurper()
    def object = jsonSlurper.parseText(result)
    if (object.outcome != 'success') {
        return object.'failure-description'
    } else {
        return null
    }
}

return this;