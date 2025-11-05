// This Jenkinsfile is used by Jenkins to run the BioModels step of Reactome's release.
// It requires that the OrthoinferenceStableIdentifierHistory and AddLinks-Insertion steps have been run successfully before it can be run.

import org.reactome.release.jenkins.utilities.Utilities

// Shared library maintained at 'release-jenkins-utils' repository.
def utils = new Utilities()

pipeline {
	agent any
	
        environment {
		    ECR_URL_ANALYSIS_CORE = 'public.ecr.aws/reactome/analysis-core'
		    CONT_NAME_ANALYSIS_CORE = 'analysis_core_container'
		    ECR_URL_BIOMODELS_MAPPER = 'public.ecr.aws/reactome/biomodels-mapper'
		    CONT_NAME_BIOMODELS_MAPPER = 'biomodels_mapper_container'
		    ECR_URL_RELEASE_BIOMODELS = 'public.ecr.aws/reactome/release-biomodels'
		    CONT_NAME_RELEASE_BIOMODELS = 'release_biomodels_container'
        }
	stages {
		// This stage checks that two upstream projects, OrthoinferenceStableIdentifierHistory and AddLinks-Insertion, were run successfully for their last build.
		stage('Check OrthoinferenceStableIdentifierHistory and AddLinks-Insertion builds succeeded'){
			steps{
				script{
					utils.checkUpstreamBuildsSucceeded("Relational-Database-Updates/job/OrthoinferenceStableIdentifierHistory")
				}
			}
		}
		stage('Setup: Pull and clean docker environment analysis core'){
                    steps{
                        sh "docker pull ${ECR_URL_ANALYSIS_CORE}:latest"
                        sh """
                           if docker ps -a --format '{{.Names}}' | grep -Eq '${CONT_NAME_ANALYSIS_CORE}'; then
                              docker rm -f ${CONT_NAME_ANALYSIS_CORE}
                           fi
                        """
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
						def analysisBinName = "analysis_v${releaseVersion}.bin"
						
						withCredentials([usernamePassword(credentialsId: 'neo4jUsernamePassword', passwordVariable: 'pass', usernameVariable: 'user')]){
							sh "mkdir -p output && rm -rf output/*"
							sh """
                                                            docker run \\
							    -v \$(pwd)/output:/output \\
							    --net=host \\
							    --name ${CONT_NAME_ANALYSIS_CORE} ${ECR_URL_ANALYSIS_CORE}:latest \\
	                                                    /bin/bash -c "java -jar target/analysis-core-exec.jar --user $user --password '\$pass\' --output /output/${analysisBinName} --verbose"
						        """
						}

						// Symlinks the analysis-biomodels-vXX.bin file to the generic analysis.bin path.
						sh "sudo chown www-data:reactome ./output/${analysisBinName}"
						sh "cp ./output/${analysisBinName} ${env.ANALYSIS_SERVICE_INPUT_ABS_PATH}/"
						sh "ln -sf ${env.ANALYSIS_SERVICE_INPUT_ABS_PATH}/${analysisBinName} ${env.ANALYSIS_SERVICE_INPUT_ABS_PATH}/analysis.bin"

						// Restart Tomcat so that the updated graph DB and analysis bin are being used.
						sh "sudo service tomcat9 stop"
						sh "sudo service tomcat9 start"
					}
				}
			}
		}

                stage('Setup: Pull and clean docker environment biomodels mapper'){
                    steps{
                        sh "docker pull ${ECR_URL_BIOMODELS_MAPPER}:latest"
                        sh """
                           if docker ps -a --format '{{.Names}}' | grep -Eq '${CONT_NAME_BIOMODELS_MAPPER}'; then
                              docker rm -f ${CONT_NAME_BIOMODELS_MAPPER}
                           fi
                        """
                    }
                }

		stage("Run BioModels mapper"){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
					dir("biomodels-mapper"){
						sh "mkdir -p output"
						sh "rm output/* -f"
						sh "mkdir -p input"
						sh "rm input/* -f"
						sh "cp /usr/local/reactomes/Reactome/production/AnalysisService/input/analysis.bin input/"
						sh """
                            docker run \\
						      -v \$(pwd)/output:/output \\
	                          -v \$(pwd)/input:/input \\
					          -v \$(pwd)/logs:/opt/biomodels-mapper/logs \\
						   --net=host --name ${CONT_NAME_BIOMODELS_MAPPER} ${ECR_URL_BIOMODELS_MAPPER}:latest \\
	                                           /bin/bash -c "java -jar -Xms5120M -Xmx10240M target/biomodels-mapper-2.0.jar -o /output/ -r /input/analysis.bin -b /tmp/BioModels_Database-r31_pub-sbml_files"
					        """
						sh "sudo chown www-data:reactome -R logs"
						sh "sudo chown www-data:reactome output/models2pathways.tsv"
						sh "sudo cp output/models2pathways.tsv ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/"
					}
				}
			}
		}

		stage('Setup: Pull and clean docker environment biomodels'){
                    steps{
                        sh "docker pull ${ECR_URL_RELEASE_BIOMODELS}:latest"
                        sh """
                           if docker ps -a --format '{{.Names}}' | grep -Eq '${CONT_NAME_RELEASE_BIOMODELS}'; then
                              docker rm -f ${CONT_NAME_RELEASE_BIOMODELS}
                           fi
                        """
                    }
                }

		// Runs the release-biomodels program that adds the BioModels cross-references to the release_current DB.
		stage('Main: Add BioModels links'){
			steps{
				script{
				        def releaseVersion = utils.getReleaseVersion()
					withCredentials([file(credentialsId: 'Config', variable: 'ConfigFile')]) {
						sh "mkdir -p input"
						sh "rm input/* -f"
						sh "cp ${env.ABS_DOWNLOAD_PATH}/${releaseVersion}/models2pathways.tsv input"
						sh """
                                                   docker run \\
						   -v \$(pwd)/input:/input \\
						   --net=host --name ${CONT_NAME_RELEASE_BIOMODELS} ${ECR_URL_RELEASE_BIOMODELS}:latest \\
	                                           /bin/bash -c "java -jar target/biomodels-*-jar-with-dependencies.jar $ConfigFile ./input/models2pathways.tsv"
						 """
					}
				}
			}
		}

		// Backs up release_current after all BioModels changes have been made.
		stage('Post: Back up DB'){
			steps{
				script{
                                        try {
					    sh "sudo service neo4j stop"
					    sh "sudo neo4j-admin dump --database=graph.db --to=biomodels_graph_database.dump"
					    sh "tar -zcf biomodels_graph_database.dump.tgz biomodels_graph_database.dump"
					    sh "rm biomodels_graph_database.dump"
	                                } finally {
					    sh "sudo service neo4j start"
	                                }
				}
			}
		}

		// Archives everything produced by this step on S3, including the biomodels graph DB and analysis core.
		stage('Post: Archive Outputs'){
			steps{
				script{
					def releaseVersion = utils.getReleaseVersion()
     
					sh "mv biomodels-mapper/logs biomodels-mapper-logs"
					sh "cp biomodels-mapper/output/models2pathways.tsv ."
					def dataFiles = ["models2pathways.tsv", "analysis-core/output/analysis_v${releaseVersion}.bin", "biomodels_graph_database.dump.tgz"]
					def logFiles = ["biomodels-mapper-logs/*", "biomodels-mapper/jsbml.log"]
					def foldersToDelete = ["analysis-core*"]

					utils.cleanUpAndArchiveBuildFiles("biomodels", dataFiles, logFiles, foldersToDelete)
				}
			}
		}
	}
}
