/**
 * Generate cookiecutter cluster by individual products
 *
 * Expected parameters:
 *   COOKIECUTTER_TEMPLATE_CREDENTIALS  Credentials to the Cookiecutter template repo.
 *   COOKIECUTTER_TEMPLATE_URL          Cookiecutter template repo address.
 *   COOKIECUTTER_TEMPLATE_BRANCH       Branch for the template.
 *   COOKIECUTTER_TEMPLATE_CONTEXT      Context parameters for the template generation.
 *   COOKIECUTTER_INSTALL_CICD          Whether to install CI/CD stack.
 *   COOKIECUTTER_INSTALL_CONTRAIL      Whether to install OpenContrail SDN.
 *   COOKIECUTTER_INSTALL_KUBERNETES    Whether to install Kubernetes.
 *   COOKIECUTTER_INSTALL_OPENSTACK     Whether to install OpenStack cloud.
 *   COOKIECUTTER_INSTALL_STACKLIGHT    Whether to install StackLight monitoring.
 *   RECLASS_MODEL_URL                  Reclass model repo address
 *   RECLASS_MODEL_CREDENTIALS          Credentials to the Reclass model repo.
 *   RECLASS_MODEL_BRANCH               Branch for the template to push to model.
 *   COMMIT_CHANGES                     Commit model to repo
 *
**/

common = new com.mirantis.mk.Common()
git = new com.mirantis.mk.Git()
python = new com.mirantis.mk.Python()

timestamps {
    node() {
        def templateEnv = "${env.WORKSPACE}/template"
        def modelEnv = "${env.WORKSPACE}/model"

        try {
            def templateContext = readYaml text: COOKIECUTTER_TEMPLATE_CONTEXT
            def templateDir = "${templateEnv}/template/dir"
            def templateBaseDir = "${env.WORKSPACE}/template"
            def templateOutputDir = templateBaseDir
            def cutterEnv = "${env.WORKSPACE}/cutter"
            def jinjaEnv = "${env.WORKSPACE}/jinja"
            def clusterName = templateContext.default_context.cluster_name
            def clusterDomain = templateContext.default_context.cluster_domain
            def targetBranch = "feature/${clusterName}"
            def outputDestination = "${modelEnv}/classes/cluster/${clusterName}"

            currentBuild.description = clusterName
            print("Using context:\n" + COOKIECUTTER_TEMPLATE_CONTEXT)

            stage ('Download Cookiecutter template') {
                git.checkoutGitRepository(templateEnv, COOKIECUTTER_TEMPLATE_URL, COOKIECUTTER_TEMPLATE_BRANCH, COOKIECUTTER_TEMPLATE_CREDENTIALS)
            }

            stage ('Download full Reclass model') {
                if (RECLASS_MODEL_URL != '') {
                    git.checkoutGitRepository(modelEnv, RECLASS_MODEL_URL, RECLASS_MODEL_BRANCH, RECLASS_MODEL_CREDENTIALS)
                }
            }

            stage('Generate base infrastructure') {
                templateDir = "${templateEnv}/cluster_product/infra"
                templateOutputDir = "${env.WORKSPACE}/template/output/infra"
                sh "mkdir -p ${templateOutputDir}"
                sh "mkdir -p ${outputDestination}"
                python.setupCookiecutterVirtualenv(cutterEnv)
                python.buildCookiecutterTemplate(templateDir, COOKIECUTTER_TEMPLATE_CONTEXT, templateOutputDir, cutterEnv, templateBaseDir)
                sh "mv -v ${templateOutputDir}/${clusterName}/* ${outputDestination}"
            }

            stage('Generate product CI/CD') {
                if (COOKIECUTTER_INSTALL_CICD.toBoolean()) {
                    templateDir = "${templateEnv}/cluster_product/cicd"
                    templateOutputDir = "${env.WORKSPACE}/template/output/cicd"
                    sh "mkdir -p ${templateOutputDir}"
                    python.setupCookiecutterVirtualenv(cutterEnv)
                    python.buildCookiecutterTemplate(templateDir, COOKIECUTTER_TEMPLATE_CONTEXT, templateOutputDir, cutterEnv, templateBaseDir)
                    sh "mv -v ${templateOutputDir}/${clusterName}/* ${outputDestination}"
                }
            }

            stage('Generate product OpenContrail') {
                if (COOKIECUTTER_INSTALL_CONTRAIL.toBoolean()) {
                    templateDir = "${templateEnv}/cluster_product/opencontrail"
                    templateOutputDir = "${env.WORKSPACE}/template/output/opencontrail"
                    sh "mkdir -p ${templateOutputDir}"
                    python.setupCookiecutterVirtualenv(cutterEnv)
                    python.buildCookiecutterTemplate(templateDir, COOKIECUTTER_TEMPLATE_CONTEXT, templateOutputDir, cutterEnv, templateBaseDir)
                    sh "mv -v ${templateOutputDir}/${clusterName}/* ${outputDestination}"
                }
            }

            stage('Generate product Kubernetes') {
                if (COOKIECUTTER_INSTALL_KUBERNETES.toBoolean()) {
                    templateDir = "${templateEnv}/cluster_product/kubernetes"
                    templateOutputDir = "${env.WORKSPACE}/template/output/kubernetes"
                    sh "mkdir -p ${templateOutputDir}"
                    python.setupCookiecutterVirtualenv(cutterEnv)
                    python.buildCookiecutterTemplate(templateDir, COOKIECUTTER_TEMPLATE_CONTEXT, templateOutputDir, cutterEnv, templateBaseDir)
                    sh "mv -v ${templateOutputDir}/${clusterName}/* ${outputDestination}"
                }
            }

            stage('Generate product OpenStack') {
                if (COOKIECUTTER_INSTALL_OPENSTACK.toBoolean()) {
                    templateDir = "${templateEnv}/cluster_product/openstack"
                    templateOutputDir = "${env.WORKSPACE}/template/output/openstack"
                    sh "mkdir -p ${templateOutputDir}"
                    python.setupCookiecutterVirtualenv(cutterEnv)
                    python.buildCookiecutterTemplate(templateDir, COOKIECUTTER_TEMPLATE_CONTEXT, templateOutputDir, cutterEnv, templateBaseDir)
                    sh "mv -v ${templateOutputDir}/${clusterName}/* ${outputDestination}"
                }
            }

            stage('Generate product StackLight') {
                if (COOKIECUTTER_INSTALL_STACKLIGHT.toBoolean()) {
                    templateDir = "${templateEnv}/cluster_product/stacklight"
                    templateOutputDir = "${env.WORKSPACE}/template/output/stacklight"
                    sh "mkdir -p ${templateOutputDir}"
                    python.setupCookiecutterVirtualenv(cutterEnv)
                    python.buildCookiecutterTemplate(templateDir, COOKIECUTTER_TEMPLATE_CONTEXT, templateOutputDir, cutterEnv, templateBaseDir)
                    sh "mv -v ${templateOutputDir}/${clusterName}/* ${outputDestination}"
                }
            }

            stage('Generate new SaltMaster node') {
                def nodeFile = "${modelEnv}/nodes/cfg01.${clusterDomain}.yml"
                def nodeString = """classes:
- cluster.${clusterName}.infra.config
parameters:
    _param:
        linux_system_codename: xenial
        reclass_data_revision: master
    linux:
        system:
            name: cfg01
            domain: ${clusterDomain}
"""
                sh "mkdir -p ${modelEnv}/nodes/"
                writeFile(file: nodeFile, text: nodeString)
            }

            stage ('Save changes to Reclass model') {
                if (COMMIT_CHANGES.toBoolean()) {
                    git.changeGitBranch(modelEnv, targetBranch)
                    git.commitGitChanges(modelEnv, "Added new cluster ${clusterName}")
                    git.pushGitChanges(modelEnv, targetBranch, 'origin', RECLASS_MODEL_CREDENTIALS)
                }
                sh(returnStatus: true, script: "tar -zcvf ${clusterName}.tar.gz -C ${modelEnv} .")
                archiveArtifacts artifacts: "${clusterName}.tar.gz"
            }

        } catch (Throwable e) {
             // If there was an error or exception thrown, the build failed
             currentBuild.result = "FAILURE"
             throw e
        } finally {
            stage ('Clean workspace directories') {
                sh(returnStatus: true, script: "rm -rfv ${templateEnv}")
                sh(returnStatus: true, script: "rm -rfv ${modelEnv}")
            }
             // common.sendNotification(currentBuild.result,"",["slack"])
        }
    }
}
