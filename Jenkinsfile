
import groovy.json.JsonSlurper
// This Jenkinsfile is used by Jenkins to run the BioModels step of Reactome's release.
// It requires that the OrthoinferenceStableIdentifierHistory step has been run successfully before it can be run.
def currentRelease
def previousRelease
pipeline {
	agent any

	stages {
  		// This stage checks that an upstream project, AddLinks-Insertion, was run successfully for its last build.
		stage('Check OrthoinferenceStableIdentifierHistory build succeeded'){
			steps{
				script{
					currentRelease = (pwd() =~ /Releases\/(\d+)\//)[0][1];
					previousRelease = (pwd() =~ /Releases\/(\d+)\//)[0][1].toInteger() - 1;
					// This queries the Jenkins API to confirm that the most recent build of OrthoinferenceStableIdentifierHistory was successful.
					def OrthoinferenceStableIdentifierHistoryStatusUrl = httpRequest authentication: 'jenkinsKey', validResponseCodes: "${env.VALID_RESPONSE_CODES}", url: "${env.JENKINS_JOB_URL}/job/${currentRelease}/job/Relational-Database-Updates/job/OrthoinferenceStableIdentifierHistory/lastBuild/api/json"
					if (OrthoinferenceStableIdentifierHistoryStatusUrl.getStatus() == 404) {
						error("OrthoinferenceStableIdentifierHistory has not yet been run. Please complete a successful build.")
					} else {
						def OrthoinferenceStableIdentifierHistoryJson = new JsonSlurper().parseText(OrthoinferenceStableIdentifierHistoryStatusUrl.getContent())
						if(OrthoinferenceStableIdentifierHistoryJson['result'] != "SUCCESS"){
							error("Most recent OrthoinferenceStableIdentifierHistory build status: " + OrthoinferenceStableIdentifierHistoryJson['result'] + ". Please complete a successful build.")
						}
					}
				}
			}
		}
		/*
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
		stage('Setup: Create reactome database from release_current') {
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]) {
						sh "mysql -u$user -p$pass -e \'drop database if exists ${env.REACTOME}; create database ${env.REACTOME}\'"
						sh "zcat ${env.RELEASE_CURRENT}_${currentRelease}_before_biomodels.dump.gz | mysql -u$user -p$pass ${env.REACTOME}"
					}
				}
			}
		}
		// This stage generates the graph database using the graph-importer module, and replaces the current graph db with it.
		stage('Setup: Generate Graph Database'){
			steps{
				script{
					cloneOrPullGitRepo("release-jenkins-utils")
					sh "cp -f release-jenkins-utils/scripts/changeGraphDatabase.sh ${env.JENKINS_HOME_PATH}"
					sh "chmod 700 ${env.JENKINS_HOME_PATH}changeGraphDatabase.sh"
					cloneOrPullGitRepo("graph-importer")
					dir("graph-importer"){
						sh "mvn clean compile assembly:single"
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "java -jar target/GraphImporter-jar-with-dependencies.jar --name ${env.REACTOME} --user $user --password $pass --neo4j ./graph.db"
							sh "tar -zcf graphdb_${currentRelease}_biomodels.tgz graph.db/"
							sh "mv graph.db /tmp/"
							sh "sudo service tomcat7 stop"
							sh "sudo service neo4j stop"
							// This static script adjusts permissions of the graph.db folder and moves it to /var/lib/neo4j/data/databases/.
							sh "sudo bash ${env.JENKINS_HOME_PATH}changeGraphDatabase.sh"
							sh "sudo service neo4j start"
							sh "sudo service tomcat7 start"
							sh "rm ${env.JENKINS_HOME_PATH}changeGraphDatabase.sh"
						}
					}
				}
			}			
		}
		*/
		stage('Setup: Generate Analysis.bin file'){
			steps{
				script{
					cloneOrPullGitRepo("analysis-core")
					dir("analysis-core"){
						def analysisBinName = "analysis-biomodels-v${currentRelease}.bin"
						sh "mvn clean compile assembly:single"
						withCredentials([usernamePassword(credentialsId: 'neo4jUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "java -jar target/analysis-core-jar-with-dependencies.jar --user $user --password $pass --output ./${analysisBinName}"
						}
											def cwd = pwd()
						sh "ln -sf ${cwd}/${analysisBinName} ${env.ANALYSIS_BIN_SYMLINK_ABS_PATH}"
					}
				}
			}
		}
		/*
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
		*/
	}		
}

// Utility function that checks if a git directory exists. If not, it is cloned.
def cloneOrPullGitRepo(String repoName) {
	// This method is deceptively named -- it can also check if a directory exists
	if(!fileExists(repoName)) {
		sh "git clone ${env.REACTOME_GITHUB_BASE_URL}/${repoName}"
	} else {
		sh "cd ${repoName}; git pull"
	}
}
