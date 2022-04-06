#!/groovy
/*
* Author: Shahar Weiss
* Date : 06.04.2022
* A very small exmaple of working with MYSql and jenkins
*/

@Library('shared-library-kit') _
import java.text.SimpleDateFormat
import java.time.*


def ownerID = '48'
def tableName = 'tableName'
def farm = "FARMNAME"
def command

pipeline {
    agent { node { label 'slave01' } }  

     parameters {
           
           string(name: 'instanceID', description: 'Enter Instance ID')
        }
    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        skipDefaultCheckout()
    }
    stages {
        stage('Cleanup') {
            steps {
                script {
                    stageOwner="email@email.com"
                    Info_Msg("Stage Owner: " + stageOwner)
                    
                }
            }
        }
        
        stage ('Verify Data'){
            steps {
                script {
                    if (instanceID == null || instanceID == "") {
                        Error_Msg("instanceID cannot be null")
                        error "AccountID cannot be null"
                    }
                    currentBuild.description = "Instance: ${instanceID}"
                }
            }
        }
        stage('Generate Command') {
            steps {
                script {
                    hostIP = getMysqlProxyVmIpByFarm(farm)//function return the ip of the farm
                    command = "SELECT count(*) FROM ${tableName} WHERE id = ${instanceID} AND owner_id = ${ownerID};"
                    def answer
                    
                    try {
                        answer = (db.executeRemoteMysqlCommand(command,hostIP)).replace("count(*)","").trim()//using ssh to remote connect and run sql query
                    } catch (Exception x) {
                       error "Breaking: ${x}"
                    }                 
                    
                    if (answer != '1'){
                        msg = "Breaking job, SQL did not return 1 instance"
                        Error_Msg(msg)
                        error (msg)
                    }

                    try {
                        command = "UPDATE ${tableName} SET is_active = 1 WHERE id = ${instanceID} AND owner_id = ${ownerID}"
                        db.executeRemoteMysqlCommand(command,hostIP)
                        
                        command = "SELECT count(*) FROM ${tableName} WHERE id = ${instanceID} AND owner_id = ${ownerID} and is_active = 1;"
                        rawAnswer = db.executeRemoteMysqlCommand(command,hostIP)
                        answer = rawAnswer.replace("count(*)","").trim()
                        if (answer == '1'){
                            Info_Msg("Successfuly enabled instance")
                        }
                        else {
                            msg = "Error, Enabling instance: ${rawAnswer}"
                            Error_Msg(msg)
                            error (msg)
                        }
                    }
                    catch (Exception x) {
                       error "Breaking: ${x}"
                    }                 
                                     

                }
            }
        }

    }

    post {
        cleanup {
            cleanWs()
        }
        success {
            script {
                Info_Msg("BUILD Succeeded")
            }
        }
        failure {
            script {
                Info_Msg("BUILD Failed")
            }
        }
        unstable {
            script {
                Error_Msg("BUILD UNSTABLE: PLEASE CHECK ERRORS IN BUILD OUTPUT")
            }
        }
    }
}



 def getMysqlProxyVmIpByFarm(farm) {
     Info_Msg("getMysqlProxyVmIpByFarm: ${farm}")
     if (farm == null || farm == ""){
            Error_Msg("farm cannot be empty")
            error "farm cannot be empty"
     }
     def mysqlProxyVmIp = null
     if (farm == "FARM1") {
         mysqlProxyVmIp = "1.2.3.4"
     }
     else if (farm == "FARM2") {
         mysqlProxyVmIp = "2.3.4.5"
     }
    if (mysqlProxyVmIp == null) {
        Error_Msg("the provided farm : ${farm} is not in the list")
        error "the provided farm : ${farm} is not in the list"
    } else {
        return mysqlProxyVmIp
    }
 }


 def executeRemoteMysqlCommand(command, hostip, port=1234) {
     Info_Msg("executeRemoteMysqlCommand: ${command} ${hostip} ${port}")
     if (hostip == null || hostip == ""){
            Error_Msg("hostip cannot be empty")
            error "hostip cannot be empty"
     }
     def passWord = getSecretFromAzureKeyVault("mysql") 
     
     def _command_output = Command_Execution_Sh(""" mysql -h ${hostip} -u 'root' -p${passWord} -P${port} -e "${command}" """)
     return _command_output
    
 }


 def getSecretFromAzureKeyVault(secretName) {
    def azureKeyVault = getAzureKeyVaultUrl()
    def spn = "jenkins-spn" // jenkins service principle + JENKINS PLUGIN
    def secrets = [
        [ $class: 'KeyVaultSecret', secretType: 'Secret', name: secretName, version: '', envVariable: 'keyVault' ]
    ]
    wrap([$class: '...',
        azureKeyVaultSecrets: ...,
        keyVaultURLOverride: ...,
        credentialIDOverride: ...
    ]) {
        return AzureKeyVaultSecret
    }
}

def Command_Execution_Sh(_command, debug=false){
    if (debug){
       Debug_Msg("Going to run [ ${_command} ]")
    }
    def _command_output = sh (
        script: "${_command}",
        returnStdout: true
    );
    return _command_output
}

def Info_Msg(message="no message"){
    _mode="INFO"
    _date=Get_Current_Date();
    echo "[${_date}] [${_mode}] ${message}"
}

// Error message
def Error_Msg(message="no message"){
    _mode="ERROR"
    _date=Get_Current_Date();
    echo "[${_date}] [${_mode}] ${message}"
}
