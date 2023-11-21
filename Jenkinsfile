// This Jenkinsfile is used by Jenkins to run the BioModels step of Reactome's release.
// It requires that the OrthoinferenceStableIdentifierHistory and AddLinks-Insertion steps have been run successfully before it can be run.

import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline {
	agent any

	stages {
		// This stage checks that two upstream projects, OrthoinferenceStableIdentifierHistory and AddLinks-Insertion, were run successfully for their last build.
		stage('Check OrthoinferenceStableIdentifierHistory and AddLinks-Insertion builds succeeded'){
			steps{
				script{
					utils.checkUpstreamBuildsSucceeded("Relational-Database-Updates/job/OrthoinferenceStableIdentifierHistory")
				}
			}
		}

		// Creates back-up of release_current DB.
		stage('Setup: Back up release_current DB'){
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.RELEASE_CURRENT_DB}", "biomodels", "before", "${env.RELEASE_SERVER}")
					}
				}
			}
		}

		// This stage generates the graph database using the graph-importer module, and replaces the current graph db with it.
		stage('Setup: Generate Graph Database'){
			steps{
				script{
					// Gets a copy of 'changeGraphDatabase.sh', which Jenkins can execute using sudo. Changes permissions of file to user read/write only and updates graph db on server.
					utils.cloneOrUpdateLocalRepo("release-jenkins-utils")
					sh "cp -f release-jenkins-utils/scripts/changeGraphDatabase.sh ${env.JENKINS_HOME_PATH}"
					sh "chmod 700 ${env.JENKINS_HOME_PATH}/changeGraphDatabase.sh"
					utils.cloneOrUpdateLocalRepo("graph-importer")

					dir("graph-importer"){
						// Build graph-importer jar file.
						utils.buildJarFileWithPackage()

						// This generates the graph database using the 'release_current' DB.
						withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							def graphDbPath = "/tmp/graph.db"
							sh "java -jar target/GraphImporter-exec.jar --name ${env.RELEASE_CURRENT_DB} --user $user --password $pass --neo4j ${graphDbPath} --interactions"
							sh "cp -r ${graphDbPath} ."
							sh "sudo service tomcat9 stop"
							sh "sudo service neo4j stop"

							// This static script adjusts permissions of the graph.db folder and moves it to /var/lib/neo4j/data/databases/.
							sh "sudo bash ${env.JENKINS_HOME_PATH}/changeGraphDatabase.sh"
							sh "sudo service neo4j start"
							sh "sudo service tomcat9 start"
							sh "rm ${env.JENKINS_HOME_PATH}/changeGraphDatabase.sh"
						}
					}
				}
			}
		}

		// Generates an intermediate analysis.bin file that is used by the biomodels-mapper.
		stage('Setup: Generate BioModels analysis.bin file'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					utils.cloneOrUpdateLocalRepo("analysis-core")

					// Builds analysis-core jar file and runs program that builds the analysis-biomodels-vXX.bin file.
					dir("analysis-core"){
						def analysisBinName = "analysis-biomodels-v${releaseVersion}.bin"

						// Builds jar file for analysis-core.
						utils.buildJarFileWithPackage()
						
						withCredentials([usernamePassword(credentialsId: 'neo4jUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "java -jar target/analysis-core-exec.jar --user $user --password $pass --output ./${analysisBinName} --verbose"
						}

						// Symlinks the analysis-biomodels-vXX.bin file to the generic analysis.bin path.
						sh "cp ${analysisBinName} ${env.ANALYSIS_SERVICE_INPUT_ABS_PATH}/"
						sh "ln -sf ${env.ANALYSIS_SERVICE_INPUT_ABS_PATH}/${analysisBinName} ${env.ANALYSIS_SERVICE_INPUT_ABS_PATH}/analysis.bin"

						// Restart tomcat so that updated graph DB and analysis bin is being used.
						sh "sudo service tomcat9 stop"
						sh "sudo service tomcat9 start"
					}
				}
			}
		}

                stage("Build Biomodels mapper"){
			steps{
				script{
					utils.cloneOrUpdateLocalRepo("biomodels-mapper")
					dir("biomodels-mapper"){
						utils.buildJarFileWithPackage()
					}
				}
			}
		}

		stage("Run BioModels mapper"){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					dir("biomodels-mapper"){
						sh "mkdir -p output"
						sh "rm output/* -f"
						sh "java -jar -Xms5120M -Xmx10240M target/biomodels-mapper-2.0.jar -o ./output/ -r /usr/local/reactomes/Reactome/production/AnalysisService/input/analysis.bin -b BioModels_Database-r31_pub-sbml_files/curated"
						sh "cp output/models2pathways.tsv ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
					}
				}
			}
		}

		// Builds jar file for the release-biomodels project.
		stage('Setup: Build release-biomodels jar file'){
			steps{
				script{
					// BioModels Jar file
					utils.buildJarFile()
				}
			}
		}

		// Runs the release-biomodels program that adds the BioModels cross-references to the release_current DB.
		stage('Main: Add BioModels links'){
			steps{
				script{
				        def releaseVersion = utils.getReleaseVersion()
					withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
						sh "java -jar target/biomodels-*-jar-with-dependencies.jar $ConfigFile ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/models2pathways.tsv"
					}
				}
			}
		}

		// Backs up release_current after all BioModels changes have been made.
		stage('Post: Back up DB'){
			steps{
				script{
					withCredentials([usernamePassword(credentialsId: 'mySQLUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
						utils.takeDatabaseDumpAndGzip("${env.RELEASE_CURRENT_DB}", "biomodels", "after", "${env.RELEASE_SERVER}")
					}
				}
			}
		}

		// Archives everything produced by this step on S3, including the biomodels graph DB and analysis core.
		stage('Post: Archive Outputs'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					def biomodelsPath = "${env.ABS_RELEASE_PATH}/biomodels"

					// Ensure that the models2pathways.tsv file is in the right place for archiving.
					sh "cp ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/models2pathways.tsv ${biomodelsPath}/models2pathways.tsv"

					// Creates tar archive out of 'graph.db/' folder that should exist in the graph-importer directory.
					dir("graph-importer"){
						utils.createGraphDatabaseTarFile("graph.db/", "biomodels")
					}
					sh "mv graph-importer/biomodels_graph_database.dump* ."

					def dataFiles = ["${biomodelsPath}/models2pathways.tsv", "analysis-core/analysis-biomodels-v${releaseVersion}.bin"]
					def logFiles = ["${biomodelsPath}/logs/*", "${biomodelsPath}/jsbml.log", "/tmp/biomodels.log", "graph-importer/parser-messages.txt"]
					def foldersToDelete = ["graph-importer*", "analysis-core*", "release-jenkins-utils*"]

					utils.cleanUpAndArchiveBuildFiles("biomodels", dataFiles, logFiles, foldersToDelete)
				}
			}
		}
	}
}
