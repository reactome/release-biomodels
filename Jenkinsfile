
import groovy.json.JsonSlurper
// This Jenkinsfile is used by Jenkins to run the BioModels step of Reactome's release.
// It requires that the OtherIdentifiers step has been run successfully before it can be run.
def currentRelease
def previousRelease
pipeline {
	agent any

	stages {
  		// This stage checks that an upstream project, AddLinks-Insertion, was run successfully for its last build.
		stage('Check OtherIdentifiers build succeeded'){
			steps{
				script{
					currentRelease = (pwd() =~ /Releases\/(\d+)\//)[0][1];
					previousRelease = (pwd() =~ /Releases\/(\d+)\//)[0][1].toInteger() - 1;
					// This queries the Jenkins API to confirm that the most recent build of OtherIdentifiers was successful.
					def otherIdentifiersStatusUrl = httpRequest authentication: 'jenkinsKey', validResponseCodes: "${env.VALID_RESPONSE_CODES}", url: "${env.JENKINS_JOB_URL}/job/$currentRelease/job/OtherIdentifiers/lastBuild/api/json"
					if (otherIdentifiersStatusUrl.getStatus() == 404) {
						error("OtherIdentifiers has not yet been run. Please complete a successful build.")
					} else {
						def otherIdentifiersJson = new JsonSlurper().parseText(otherIdentifiersStatusUrl.getContent())
						if(otherIdentifiersJson['result'] != "SUCCESS"){
							error("Most recent OtherIdentifiers build status: " + otherIdentifiersJson['result'] + ". Please complete a successful build.")
						}
					}
				}
			}
		}
		stage('Setup: Back up DB'){
			steps{
				script{
          withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
            def release_current_before_biomodels_dump = "${env.RELEASE_CURRENT}_${currentRelease}_before_biomodels.dump"
            sh "mysqldump -u$user -p$pass ${env.RELEASE_CURRENT} > $release_current_before_biomodels_dump"
            sh "gzip -f $release_current_before_biomodels_dump"
          }
				}
			}
		}
		stage('Setup: Build jar file'){
			steps{
				script{
          sh 'mvn clean compile assembly:single'
				}
			}
		}
		stage('Main: Generate BioModels file'){
			steps{
				script{
					dir("${env.ABS_RELEASE_PATH}/biomodels/"){
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "perl biomodels.pl -db ${env.RELEASE_CURRENT}"
						}
					}
				}
			}
		}
		stage('Main: Add BioModels links'){
			steps{
				script{
          withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
            sh "java -jar target/biomodels-*-jar-with-dependencies.jar $ConfigFile ${env.ABS_RELEASE_PATH}/biomodels/models2pathways.tsv"
          }
				}
			}
		}
		stage('Post: Back up DB'){
			steps{
				script{
          withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
            def release_current_after_biomodels_dump = "${env.RELEASE_CURRENT}_${currentRelease}_after_biomodels.dump"
            sh "mysqldump -u$user -p$pass ${env.RELEASE_CURRENT} > $release_current_after_biomodels_dump"
            sh "gzip -f $release_current_after_biomodels_dump"
          }
				}
			}
		}
		stage('Post: Archive logs and backups'){
			steps{
				script{
          sh "mkdir -p archive/${currentRelease}/logs"
          sh "mv --backup=numbered *_${currentRelease}_*.dump.gz archive/${currentRelease}/"
          sh "gzip logs/*"
          sh "mv logs/* archive/${currentRelease}/logs/"
				}
			}
		}
	}		
}
		
		
