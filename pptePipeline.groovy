def gitRepo = 'https://github.com/bonjo7/running-social-node-app.git'
def projectName = 'running-social-ppte'
def appName = 'running-social-node-app'
def linebreak = '---------------------------------------'
def brnach = true
def didTimeout = false

node('nodejs') {
    
    //Stage requiring the user to enter the branch name
    stage('Select branch') {
          //Set time out to prevent pipeline waiting for user input
          timeout(time: 60, unit: 'SECONDS') {
              branch = input(
                id: 'branch', message: 'Select branch to build from: ', 
                parameters: [
                [$class: 'StringParameterDefinition', defaultValue: 'None', description: '...', name: 'Enter name of branch: '],
                ]
                )
                //tell the user which branch they have selected
                echo linebreak
                echo ("You selected git branch: " + branch)
                echo linebreak
          }
    }

  //Clone the source code
    stage('Checkout Git Repo') {
        
            echo linebreak
            echo 'Cloning from repo ' + gitRepo + ' from branch ' + branch
            echo linebreak
                //using git url and branch for clone
                git branch: branch,
                url: gitRepo
                
    }

    //Build the code
    stage('Build Code') {
            
            echo linebreak
            echo 'Installing dependencies, Running npm install'
            echo linebreak

                //After npm installs the app is built in a target folder
                //Delete the target folder and re create and tar the app into the target folder
                sh "npm install"
                sh 'ls -a'
                sh 'rm -rf target; mkdir target; tar --exclude=".git" --exclude="./node_modules" -cvf target/archive.tar ./ || [[ $? -eq 1 ]]'
   
    }

    //Test the code
    stage('Code Testing'){
            echo "Running Automated Test"
            //run the test script
            sh "npm test"
    }
    
    //Deploy into the testing envoirnment
    stage ('Deploy in PPTE'){
        
         openshift.withCluster() {
         openshift.withProject(projectName) {
           //Roll out the latest build
            openshift.selector("dc", appName).rollout().latest()
            //Scale the build to one pod
            openshift.selector("dc", appName).scale("--replicas=1")
          }
      }

    }
    
    //Create a new image
    stage("Build image in PPTE") {

      openshift.withCluster() {
        openshift.withProject(projectName) {

          //Build the new image and tag it which will be used to deploy to production
          openshift.selector("bc/${appName}").startBuild("--from-archive=target/archive.tar", "--wait")
          openshift.tag("${projectName}/${appName}:latest", "${projectName}/${appName}:latest")
        }
      }
    }
    

    stage('Promote and Deploy to Prod') {
        openshift.withCluster() {
          
           openshift.withProject("running-social-prod") {
             //Production deployment is set to pull the image from the ppte envoirnment
            openshift.selector("dc", "${appName}").rollout().latest()
            openshift.selector("dc", "${appName}").scale("--replicas=1")
           }
        }
    }

}


