def gitRepo = 'https://github.com/bonjo7/running-social-node-app.git'
def projectName = 'running-social-dev'
def appName = 'running-social-node-app'
def linebreak = '---------------------------------------'
def branch = 'develop'


node('nodejs') {
    
    def stashedArtifactName = 'npm-build-archive'
    
    stage('Checkout Git Repo') {
        
            echo linebreak
            echo 'Cloning from repo ' + gitRepo + ' from branch ' + branch
            echo linebreak
            
                git branch: branch,
                url: gitRepo
                
    }
    stage('build') {
            
            echo linebreak
            echo 'Installing dependencies, Running npm install'
            echo linebreak
           
                sh "npm install"
                sh 'ls -a'
                sh 'rm -rf target; mkdir target; tar --exclude=".git" --exclude="./node_modules" -cvf target/archive.tar ./ || [[ $? -eq 1 ]]'
                stash name: stashedArtifactName, includes: "target/*"
    }
    
    stage("Build image in Dev'") {

      openshift.withCluster() {
        openshift.withProject(projectName) {
        unstash name: stashedArtifactName
          def dc = openshift.selector("dc/running-social-node-app")
          if(dc.exists()) {
              echo "Removing triggers for Deployment config"
              openshift.set("triggers", "dc/running-social-node-app", "--from-config", "--remove")
          } else {
              echo "No deployment with name running-social-node-app exists"
          }

          openshift.selector("bc/${appName}").startBuild("--from-archive=target/archive.tar", "--wait")
          openshift.tag("${projectName}/${appName}:latest", "${projectName}/${appName}:latest")
        }
      }
    }

    
    stage ('deployInDevelopment'){
        
         openshift.withCluster() {
         openshift.withProject(projectName) {
            openshift.selector("dc", appName).rollout().latest()
            openshift.selector("dc", appName).scale("--replicas=1")
          }
      }

    }

}




