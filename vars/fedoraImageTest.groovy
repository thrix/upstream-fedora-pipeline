def call(Map parameters = [:]) {

    def fedoraRelease = parameters.get('fedoraRelease')
    def imageType = parameters.get('imageType', 'Cloud')

    def libraries = ['contra-lib': ['master', 'https://github.com/openshift/contra-lib.git']]

    libraries.each { name, repo ->
        library identifier: "${name}@${repo[0]}",
                retriever: modernSCM([$class: 'GitSCMSource',
                                      remote: repo[1]])

    }

    properties(
            [
                    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '10', daysToKeepStr: '', numToKeepStr: '10')),
                    pipelineTriggers(
                            [[
                                     $class: 'CIBuildTrigger',
                                     checks: [
                                             [expectedValue: 'Fedora-Cloud$|Fedora$', field: 'release_short'],
                                             [expectedValue: 'ga', field: 'release_type'],
                                             [expectedValue: 'FINISHED', field: 'status'],
                                             [expectedValue: fedoraRelease, field: 'release_version']
                                     ],
                                     providerName: 'fedora-fedmsg', selector: 'topic = "org.fedoraproject.prod.pungi.compose.status.change"'
                             ]]
                    ),
                    parameters(
                            [
                                    string(defaultvalue: "", description: 'CI_MESSAGE', name: 'CI_MESSAGE'),
                                    string(defaultvalue: "", description: 'MESSAGE_HEADERS', name: 'MESSAGE_HEADERS')
                            ]
                    )
            ]
    )

    imageName = null
    containers = ['singlehost-test']

    try {
        deployOpenShiftTemplate(podName: "fedora-image-test-${UUID.randomUUID().toString()}",
                docker_repo_url: '172.30.254.79:5000',
                containers: containers) {
            stage('download image') {
                sh 'env'
                imageName = downloadCompose(imageType: imageType)
            }

            stage('test compose') {
                testCompose(imageName: imageName)
            }

            stage('archive image') {
                handlePipelineStep {
                    step([$class   : 'ArtifactArchiver', allowEmptyArchive: true,
                          artifacts: '*.qcow2', fingerprint: true])
                }
            }
        }
    } catch(e) {
        echo e.getMessage()
        throw e
    }

}
