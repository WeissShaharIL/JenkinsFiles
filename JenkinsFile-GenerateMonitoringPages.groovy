#!/groovy
/*
Date : 07.02.2022
Author : Shahar Weiss
Job is get trigged by job called : Application-CheckServiceBusErrors.
it uses the data passed by the former job to create a static html page.
it then continue to the second part where it builds another 2 static web pages by connecting to azure aplication insight and downloading data
from failures queues. after all data is present, the job updates webapp to deliver the static web pages

*/
@Library('shared-library-kit') _
import java.time.*
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import groovy.json.JsonSlurperClassic



def teamsURL = "..."

//This part is so for the ServiceBus html build
def srvsBusRenamingTable = ["web" : "Monolith"]
def srvsBusFullData = [][]
def srvsBusProdNamespaces = ['...','...','...','...','...']
def spacer = """<tr>              
                %%%SPACERDATA%%%
                </tr> """.stripMargin()
def spacerData = """<th style="background-color: #ADD8E6; padding:30px"></th>\n"""
def srvsBusHtmlStart = """<html>
                <head>
                <title>ServiceBus Monitor</title>
                <style>
                    .blink {
                    animation: blinker 2s linear infinite;
                    color: red;
                    font-size: 30px;
                    font-weight: bold;
                    font-family: sans-serif;
                    }
                    @keyframes blinker {
                    50% {
                        opacity: 0;
                    }
                    }
                    .blink-one {
                    animation: blinker-one 1s linear infinite;
                    }
                    @keyframes blinker-one {
                    0% {
                        opacity: 0;
                    }
                    }
                  
                    .hoverTable{
                    width:100%; 
                    table-layout: auto;
                    border: 1px solid black;
                    border-collapse:collapse; 
                        }
                        .hoverTable td{ 
                            padding:7px; border:#000000 1px solid;
                        }
                        /* Define the default color for all the table rows */
                        .hoverTable tr{
                            background: #cff0fa;
                        }
                        /* Define the hover highlight color for the table row */
                        .hoverTable tr:hover {
                            background-color: #ffffff;
                        }
                    
                    
                </style>
                </head>
                <h1><center>ServiceBus Error Queues Monitor</center></h1>""".stripMargin()
    
def srvsBusFirstHtmlTable = """<table class="hoverTable">

                    <body style="background-color: #cff0fa;">
                    <tr style="background-color: #dcedf2;">
                    %%%FIRSTHTMLTABLE%%%</tr>
                    <tr>
                    """.stripMargin()
 
def srvsBusHtmlProd = "<tr>\n"
def srvsBusHtmlDev = "<tr>\n"
def srvsBusHtmlEnd = """</body>
                </table>
                </html>""".stripMargin()

def nameSpaces=[]
def linesFullData=[]
def devServices=[]
def serviceList=[]
def txt
//End of serviceBus html build

//Application insight part

def indexHTML = """<html>
    <head>
        <style>
             .button {
        position: relative;
        background-color: #4285F4;
        border: none;
        font-size: 12px;
        color: #FFFFFF;
        padding: 2px;
        width: 80px;
        height: 20px;
        text-align: center;
        transition-duration: 0.4s;
        text-decoration: none;
        overflow: hidden;
        cursor: pointer;
      }
      
      .button:after {
        content: "";
        background: #f1f1f1;
        display: block;
        position: absolute;
        padding-top: 300%;
        padding-left: 350%;
        margin-left: -20px !important;
        margin-top: -120%;
        opacity: 0;
        transition: all 0.8s
      }
      
      .button:active:after {
        padding: 0;
        margin: 0;
        opacity: 1;
        transition: 0s
      }
      pre {
        font-size: 16px;
    }
    </style>

    <title>Monitor</title>
    <h1 style="text-align: center;">DevOps Monitoring Page</h1>
    <body style="background-color: #cff0fa;">
    <pre>View Application Insight failures - By roles (PROD)       <button class="button" onclick="window.location.href='appInsight24.html'">24Hrs</button> <button class="button" onclick="window.location.href='appInsight7.html'">Week</button></pre>
    <pre>View Application Insight failures - By roles (STG)        <button class="button" onclick="window.location.href='STG-appInsight24.html'">24Hrs</button> <button class="button" onclick="window.location.href='STG-appInsight7.html'">Week</button></pre>
    <pre>View service bus error queue (Updated every ~5 min)       <button class="button" onclick="window.location.href='servicebusmonitor.html'">View</button></pre>
    <pre>Application insight - DDos Attack                         <button class="button" onclick="window.location.href='appddos.html'">View</button></pre>
    </body>
    </head>
    </html>""".stripMargin()

def resourceGroup 
def roles = ["Service1","Service2",'Service3','Service4','Service5']
def farmsAppinsight = ["farm1","farm1-STG"]
def htmlStart ="""<html>
  <head>
    <title>Service-Exception monitor</title>
     <style>
      .button {
        position: relative;
        background-color: #4285F4;
        border: none;
        font-size: 14px;
        color: #FFFFFF;
        padding: 10px;
        width: 80px;
        height: 40px;
        text-align: center;
        transition-duration: 0.4s;
        text-decoration: none;
        overflow: hidden;
        cursor: pointer;
      }
      
      .button:after {
        content: "";
        background: #f1f1f1;
        display: block;
        position: absolute;
        padding-top: 300%;
        padding-left: 350%;
        margin-left: -20px !important;
        margin-top: -120%;
        opacity: 0;
        transition: all 0.8s
      }
      
      .button:active:after {
        padding: 0;
        margin: 0;
        opacity: 1;
        transition: 0s
      }
      </style>
    <h1 style="text-align: center;">Top exception types (Last %%%UPDATETIME%%%)</h1>
    <body style="background-color: #cff0fa;">


    <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
    <script type="text/javascript">

      // Load Charts and the corechart package.
      google.charts.load('current', {'packages':['corechart']});""".stripMargin()
def htmlEnd = """<button class="button" onclick="Pie()">Pie</button>
                <button class="button" onclick="Coloums()">Coloums</button>
    <p><small>Generated : %TIME% (GMT +0)</small></p></body>
    </body>
    </html>""".stripMargin() 
def googleChartsTxt=""
def googleChartsTxtPie=""
def tableTD=""
def textToAdd = ""
def allFunctions = ""
def htmlTablePart = ""
def endFunction = """function Pie() {
            %FUNCTIONPIEDATA%
            }


            function Coloums() {
            %FUNCTIONCOLDATA%
            }
            Coloums()
 </script>\n </head>\n<body>\n""".stripMargin()

def itemPerRow = 3
def functionTemplate="""function %FUNCNAME%() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', 'Name');
        data.addColumn('number', 'Errors');
        data.addRows([
        %ROWSDATA%
         ]);
        var options = {title:'%TITLE%',
                       width:600,
                       height:200,
                       animation:{
                         "startup": true,
                          duration: 1500,
                          easing: 'out'}
                        };
        var chart = new google.visualization.ColumnChart(document.getElementById('%ELEMENT%'));
        chart.draw(data, options);}""".stripMargin()
    

//End of Application insight part

//DDos Part

def mapArrayVsFarm = [0 : "FARM1",
              1 : "FARM2", 
              2 : "FARM3",
              3 : "FARM4", 
              4 : "FARM5"
              ]


def mapData = ["FARM1": 'farm1-AI',
              "FARM2": 'farm1-AI', 
              "FARM3": "farm2-AI",
              "FARM4": "farm3-AI", 
              "FARM5": "farm4-AI"
              ]



def badIpHtml = """<!DOCTYPE html>
            <html>
            <head>
            <title>AI Monitor - DDos</title>

            <meta name="viewport" content="width=device-width, initial-scale=1">

            <style>
            table {
            font-family: arial, sans-serif;
            border-collapse: collapse;
            width: 50%;
            }

            td, th {
            border: 1px solid #000000;
            text-align: center;
            padding: 8px;
            }

            tr:nth-child(even) {
              background-color: #b4cfd8;
            }
               .tab {
             overflow: hidden;
             border: 1px solid #ccc;
             background-color: #b4cfd8;
             width: 30%;
            }


            /* Style the buttons inside the tab */
            .tab button {
            background-color: inherit;
            float: left;    
            border: none;
            outline: none;
            cursor: pointer;
            padding: 14px 16px;
            transition: 0.3s;
            font-size: 17px;
            }

            /* Change background color of buttons on hover */
            .tab button:hover {
            background-color: #ffffff;
            }

            /* Create an active/current tablink class */
            .tab button.active {
            background-color: #ffffff;
            }

            /* Style the tab content */
            .tabcontent {
            display: none;
            padding: 6px 12px;
            border: 1px solid #ccc;
            border-top: none;
            }

            </style>
            </head>
            <body style="background-color: #cff0fa;">
            <h1><center>Application insight - Top by Client IP & by URL</center></h1>
            
            <p>Choose metric:</p>

            <div class="tab">
            <button id="mybutton" class="tablinks" onclick="openTable(event, 'Client_IP')">Client_IP</button>
            <button class="tablinks" onclick="openTable(event, 'URL')">URL</button>
            </div>

            %%%DATA%%%

            <br>
            <p><small>Generated : %TIME% (GMT +0)</small></p></body>
            <script>
            function openTable(evt, metric) {
            var i, tabcontent, tablinks;
            tabcontent = document.getElementsByClassName("tabcontent");
            for (i = 0; i < tabcontent.length; i++) {
                tabcontent[i].style.display = "none";
            }
            tablinks = document.getElementsByClassName("tablinks");
            for (i = 0; i < tablinks.length; i++) {
                tablinks[i].className = tablinks[i].className.replace(" active", "");
            }
            document.getElementById(metric).style.display = "block";
            evt.currentTarget.className += " active";
            }
            document.addEventListener("DOMContentLoaded", function(event) { 
            document.getElementById("mybutton").click(); });
            </script>
            </body>
            </html>""".stripMargin()

def htmlDataReplace = ""

def appName
def whiteListIps = ['1.2.3.4']

def threshHold = 50
def urlThreshHold = 20
def offset = "1h"
def farmsData = []
def urlData = []
//End of DDos Part

def timeStamp = Get_Current_Date()


    pipeline {
        agent { node { label 'general-new'} }
        
        parameters {
            string(name: 'data', description: 'data')
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
                         stageOwner="DevOps@mail.com"
                         kit.Info_Msg("Stage Owner: " + stageOwner)
                         kit.Info_Msg("Fetching Data from roles: ${roles}")                    
                         kit.Info_Msg("Servicebus Html : ${data}")                    
                        
                }
            }
        }

        stage('ServiceBus - Data manipulation') {
            steps {
                script {
                    data = data.replaceAll("-----ENDOFNAMESPACE-----","-----ENDOFNAMESPACE-----\n").replaceAll("\'\\{\\\\\'","\n").replaceAll("\\\\':\\\\\'",":").replaceAll("\\\\\',\\\\\'","\n")
                    kit.Info_Msg("Data: " + data)
                }
            }
        }
        
        stage ('Build services array and manipulate first table + build spacer row'){
            steps {
                script {
                    loopSwitch = false
                    startAdding = false
                    // we need to take the full service list from develop farm
                    for (line in data.split("\n")) {
                        if (line == 'develop'){
                            loopSwitch = true
                        }
                        if (loopSwitch){                                 
                            if (line == "-----ENDOFNAMESPACE-----"){
                                break
                            }
                            txt = line.split(":")[0].replace("-error","")
                            if (srvsBusRenamingTable.containsKey(txt)){
                                txt = srvsBusRenamingTable[txt]
                            }
                            if (startAdding){
                                serviceList.add(txt.capitalize())
                            }
                            startAdding = true
                        }
                               
                    }
                    kit.Info_Msg("All services: " + serviceList)
                    txt="""<td style="width: 200px; text-align: center;"><strong>UPDATED: ${timeStamp}</strong></td>\n"""
                    for (service in serviceList){
                        txt += """<td style="width: 200px; font-size:120%; text-align: center;"><strong>${service}</strong></td>\n"""
                        spacerData += """<th style="background-color: #ADD8E6; padding:30px"></th>\n"""
                    }
                    srvsBusFirstHtmlTable = srvsBusFirstHtmlTable.replace("%%%FIRSTHTMLTABLE%%%",txt)
                    spacer = spacer.replace("%%%SPACERDATA%%%",spacerData)
                    }
                }
            }
            
            stage('Convert Data 2d Array') {
                steps {
                    script {
                        index = 0
                        def dataToAdd = []
                        
                        for (line in data.split("\n")){
                            if (line == "-----ENDOFNAMESPACE-----"){
                                srvsBusFullData.add(dataToAdd)
                                dataToAdd = []
                            }
                            else {
                                txt = line.replace("-error","")
                                if (srvsBusRenamingTable.containsKey(txt.split(":")[0])){
                                    //rename if needed
                                    temp = txt.split(":")[0]
                                    txt = txt.replace(temp,srvsBusRenamingTable[temp])
                                }
                                dataToAdd.add(txt.capitalize())
                            }
                        }
                        kit.Info_Msg("Full data: " + srvsBusFullData)
                    }
                }
            }
            
            stage('Build prod table') {
                steps {
                    script {
                        for (item in srvsBusFullData){
                            if (item[0].toLowerCase() in srvsBusProdNamespaces){
                                name = item[0].toUpperCase()
                                srvsBusHtmlProd += """<td style="width: 200px; font-size:130%; text-align: center; background-color: #dcedf2;"><strong>${name}</strong></td>\n"""
                                for (service in serviceList){
                                    found = false
                                    for (data in item){
                                        if (data.split(":")[0] == service){
                                            found = true
                                            num = data.split(":")[1]
                                            break 
                                        }
                                    }
                                    if (found){
                                        if (num == "0"){
                                            srvsBusHtmlProd += """<td style="width: 200px; font-size:130%; height: 40px; text-align: center;"><span style="color: #000000; background-color: #00ff00;">Good</span></td>\n"""
                                        } 
                                        else {
                                            srvsBusHtmlProd += """<td style="width: 200px; height: 40px; text-align: center;"><p class="blink">${num}</p></td>\n"""
                                        }
                                    } else {
                                        srvsBusHtmlProd += """<td style="width: 200px; height: 40px; text-align: center;"><span style="color: #000000">---</span></td>\n"""
                                      }
                                }
                                srvsBusHtmlProd += "</tr>\n"
                            }

                            else {
                                name = item[0].toUpperCase()
                                srvsBusHtmlDev += """<td style="width: 200px; font-size:130%; text-align: center; background-color: #dcedf2;"><strong>${name}</strong></td>"""
                                for (service in serviceList){
                                    found = false
                                    for (data in item){
                                        if (data.split(":")[0] == service){
                                            found = true
                                            num = data.split(":")[1]
                                            break 
                                        }
                                    }
                                    if (found){
                                        if (num == "0"){
                                            srvsBusHtmlDev += """<td style="width: 200px; font-size:130%; height: 40px; text-align: center;"><span style="color: #000000; background-color: #00ff00;">Good</span></td>\n"""
                                        } 
                                        else {
                                            srvsBusHtmlDev += """<td style="width: 200px; height: 40px; text-align: center;"><p class="blink">${num}</p></td>\n"""
                                        }
                                    } else {
                                        srvsBusHtmlDev += """<td style="width: 200px; height: 40px; text-align: center;"><span style="color: #000000">---</span></td>\n"""
                                      }
                                }
                            srvsBusHtmlDev += "</tr>\n"
                            }
                        }
                      
                    }
                }
            }
            stage('Assemble ServiceBus HTML and write to disk') {
                steps {
                    script {
                        fullTxt = srvsBusHtmlStart+srvsBusFirstHtmlTable+srvsBusHtmlProd+spacer+srvsBusHtmlDev+srvsBusHtmlEnd
                        writeFile(file: 'servicebusmonitor.html', text: fullTxt)
                    }
                }
            }
            stage ('Application Insight - Get RG + Add AZ extension'){
                steps {
                    script {
                 
                    try {
                        kit.Command_Execution_Sh("az extension add --name application-insights")
                        } catch (Exception x) {
                            def msg = "Error cannot download az extension: ${x}"
                            kit.Error_Msg(msg)
                            error msg
                            }
                    }  
                }          
            }

            stage ('Build First HTML'){
                steps {
                    script {
                        for (farm in farmsAppinsight){
                               
                               if (farm == "FARM1"){
                                   app = "farm1-AI"
                               }
                               else if (farm == "FARM1-STG") {
                                   app = "farm1-stg-AI"
                               }
                               resourceGroup = azure.getAzureResourceGroupByResource(app, farm) //this also set the subscription
                        
                                for (role in roles){
                                    command = """az monitor app-insights query --app ${app} --resource-group ${resourceGroup} --analytics-query "exceptions | where (cloud_RoleName == '${role}') | summarize _count=sum(itemCount) by type" --offset 24h"""
                                    extraFuncName = role+"PieChart"
                                    writeFile(file: 'command.sh', text: command)
                                    kit.Command_Execution_Sh_New("chmod +x command.sh")
                                    googleChartsTxt += "google.charts.setOnLoadCallback(${role});\n"
                                    googleChartsTxtPie += "google.charts.setOnLoadCallback(${extraFuncName});\n"
                                    tableTD += """<td><div id="${role}" style="border: 5px solid #ccc"></div></td>\n"""
                                    textToAdd = ""
                                    answer = kit.Command_Execution_Sh_New("./command.sh",debug=false, clean=false)
                                    def slurper = new JsonSlurper().parseText(answer)
                                    index=0
                                    for (item in slurper.tables.rows){
                                        while (item[index] != null){
                                            textToAdd += item[index]
                                            index++
                                        }
                                        //We need to modify the text so it would fit the HTML
                                        textToAdd = textToAdd.replace("][","],\n\t[").replace("[","['").replace(", ","', ")
                                    }
                                    allFunctions += functionTemplate.replace("%FUNCNAME%",role).replace("%TITLE%",role).replace("%ELEMENT%",role).replace("%ROWSDATA%",textToAdd) +"\n"
                                    allFunctions += functionTemplate.replace("%FUNCNAME%",extraFuncName).replace("%TITLE%",role).replace("%ELEMENT%",role).replace("%ROWSDATA%",textToAdd).replace("visualization.ColumnChart","visualization.PieChart") +"\n"
                                    }
                                    
                                    //Build the tables
                                    def counter = 0
                                    while (roles[counter] != null){
                                        htmlTablePart += """<table class="columns">\n\t<tr>\n\t"""
                                        for (i=1; i <= itemPerRow; i++){
                                            if (counter == roles.size()){
                                                break
                                            }
                                            htmlTablePart += tableTD.split("\n")[counter]+"\n\t"
                                            counter++
                                            }
                                            htmlTablePart += """</tr>\n\t</table>\n"""
                                    }

                                    //build final HTML
                                    htmlEnd = htmlEnd.replace("%TIME%",timeStamp)
                                    endFunction = endFunction.replace("%FUNCTIONPIEDATA%",googleChartsTxtPie).replace("%FUNCTIONCOLDATA%",googleChartsTxt)
                                    finalHtml = (htmlStart+googleChartsTxt+allFunctions+endFunction+htmlTablePart+htmlEnd).replace("%%%UPDATETIME%%%","24 Hours")

                                    filename = "${farm}-appInsight24.html"
                                    writeFile(file: filename, text: finalHtml)
                                    
                                    //Remove command.sh, next step we're uploading the dir content
                                    kit.Command_Execution_Sh("rm command.sh") 
                        }
                    }           
                }
            }

            stage ('Build Second HTML'){
                steps {
                    script {
                        //reset the variables
                        googleChartsTxt=""
                        googleChartsTxtPie=""
                        tableTD=""
                        allFunctions=""
                        htmlTablePart = ""
                          for (farm in farmsAppinsight){
                               if (farm == "FARM1"){
                                   app = "farm1-AI"
                               }
                               else if (farm == "FARM1-STG") {
                                   app = "farm1-stg-AI"
                               }
                               resourceGroup = azure.getAzureResourceGroupByResource(app, farm) //this also set the subscription


                        
                                for (role in roles){
                                    command = """az monitor app-insights query --app ${app} --resource-group ${resourceGroup} --analytics-query "exceptions | where (cloud_RoleName == '${role}') | summarize _count=sum(itemCount) by type" --offset 7d"""
                                    extraFuncName = role+"PieChart"
                                    writeFile(file: 'command.sh', text: command)
                                    kit.Command_Execution_Sh_New("chmod +x command.sh")
                                    googleChartsTxt += "google.charts.setOnLoadCallback(${role});\n"
                                    googleChartsTxtPie += "google.charts.setOnLoadCallback(${extraFuncName});\n"
                                    tableTD += """<td><div id="${role}" style="border: 5px solid #ccc"></div></td>\n"""
                                    textToAdd = ""
                                    answer = kit.Command_Execution_Sh_New("./command.sh",debug=false, clean=false)
                                    def slurper = new JsonSlurper().parseText(answer)
                                    index=0
                                    for (item in slurper.tables.rows){
                                        while (item[index] != null){
                                            textToAdd += item[index]
                                            index++
                                    }
                                    //We need to modify the text so it would fit the HTML
                                    textToAdd = textToAdd.replace("][","],\n\t[").replace("[","['").replace(", ","', ")
                                }
                                    allFunctions += functionTemplate.replace("%FUNCNAME%",role).replace("%TITLE%",role).replace("%ELEMENT%",role).replace("%ROWSDATA%",textToAdd) +"\n"
                                    allFunctions += functionTemplate.replace("%FUNCNAME%",extraFuncName).replace("%TITLE%",role).replace("%ELEMENT%",role).replace("%ROWSDATA%",textToAdd).replace("visualization.ColumnChart","visualization.PieChart") +"\n"
                                }
                                //Build the tables
                                def counter = 0
                                while (roles[counter] != null){
                                    htmlTablePart += """<table class="columns">\n\t<tr>\n\t"""
                                    for (i=1; i <= itemPerRow; i++){
                                        if (counter == roles.size()){
                                            break
                                        }
                                        
                                        htmlTablePart += tableTD.split("\n")[counter]+"\n\t"
                                        counter++
                                    }
                                    htmlTablePart += """</tr>\n\t</table>\n"""
                                }
                                //build final HTML

                                htmlEnd = htmlEnd.replace("%TIME%",timeStamp)
                                endFunction = endFunction.replace("%FUNCTIONPIEDATA%",googleChartsTxtPie).replace("%FUNCTIONCOLDATA%",googleChartsTxt)
                                finalHtml = (htmlStart+googleChartsTxt+allFunctions+endFunction+htmlTablePart+htmlEnd).replace("%%%UPDATETIME%%%","7 Days")
                                //change the chart

                                filename = "${farm}-appInsight7.html"
                                writeFile(file: filename, text: finalHtml)

                               
                                //Remove command.sh, next step we're uploading the dir content
                                kit.Command_Execution_Sh("rm command.sh") 
                          }
                    }           
                }
            }
            
            stage ('DDos Part get Data'){
                steps {
                    script {
                        query = """requests | where cloud_RoleName == 'Monolith-Front' | summarize Count=count() by client_IP | order by Count desc | where Count > ${threshHold}"""
                        mapData.each{
                            key, value -> 
                            kit.Info_Msg("Running on $key the app name is + $value")
                        
                            farm = key
                            appName = value
                            resourceGroup = azure.getAzureResourceGroupByResource(value, farm)
                            command = """az monitor app-insights query --app ${appName} --resource-group ${resourceGroup} --analytics-query " ${query} " --offset ${offset} """ 
                            answer = azure.executeAzureCliCommand_New(command, farm,clean=false)
                            def slurper = new JsonSlurperClassic().parseText(answer)
                            for (item in slurper.tables.rows){
                                farmsData.add(item)
                            }
                        }
                        
                        query = """requests | where cloud_RoleName == 'Monolith-Front' | summarize Count=count() by url | order by Count desc | where Count > ${urlThreshHold}"""
                        mapData.each{
                            key, value -> 
                            kit.Info_Msg("Running on $key the app name is + $value")
                            
                            farm = key
                            appName = value
                            resourceGroup = azure.getAzureResourceGroupByResource(value, farm)
                            command = """az monitor app-insights query --app ${appName} --resource-group ${resourceGroup} --analytics-query " ${query} " --offset ${offset} """ 
                            answer = azure.executeAzureCliCommand_New(command, farm,clean=false)
                            def slurper = new JsonSlurperClassic().parseText(answer)
                            for (item in slurper.tables.rows){
                                urlData.add(item)
                            }
                         
                        }
                     
                    }
                }
            }
             stage('Build DDOS Html') {
                 steps {
                     script {
                         index = 0
                         htmlDataReplace += """<div id="Client_IP" class="tabcontent">\n<h2><center>By Client IP</center></h2>\n"""
                         for (item in farmsData){
                             currentFarm = (mapArrayVsFarm.get(index))
                             index++
                        
                        if (!item.isEmpty()){
                            htmlDataReplace += "<h3>&emsp;${currentFarm}</h3>\n<table>\n <tr>\n<th>IP</th>\n    <th>Requests</th>\n    <th>Country</th>\n  </tr>\n"
                            for (line in item){
                                ip = line[0]
                              
                                    requests = line[1]
                                    location = GetLocation(ip)
                                    if (requests > 100){
                                        teamsMessage = "Possible DDoS Attack coming up: Farm : ${currentFarm} From ${ip} Location: ${location}\nRquests in the last hour : ${requests}"
                                        office365ConnectorSend message: teamsMessage, status:"Alert", webhookUrl: teamsURL
                                    }
                                htmlDataReplace += " <tr>\n    <td>${ip}</td>\n    <td>${requests}</td>\n    <td>${location}</td>\n  </tr>\n"
                                //}
                               
                        }
                        htmlDataReplace += "</table>\n"
                        } else {
                            htmlDataReplace += "<h3>&emsp;${currentFarm}</h3>\n<p>Did not found any ip that has more than ${threshHold} requests in the last ${offset}</p>\n"
                        }
                    
                    }
                    htmlDataReplace += """</div>\n<div id="URL" class="tabcontent"><h2><center>By URL</center></h2>"""
                    index = 0
                    
                    for (item in urlData){
                        currentFarm = (mapArrayVsFarm.get(index))
                        index++
                        
                        if (!item.isEmpty()){
                            htmlDataReplace += "<h3>&emsp;${currentFarm}</h3>\n<table>\n <tr>\n<th>URL</th>\n    <th>Requests</th>\n    </tr>\n"
                            for (line in item){
                                url = line[0]
                                requests = line[1]
                                htmlDataReplace += " <tr>\n    <td>${url}</td>\n    <td>${requests}</td>\n   </tr>\n"
                        }
                        htmlDataReplace += "</table>\n"
                        } else {
                            htmlDataReplace += "<h3>&emsp;${currentFarm}</h3>\n<p>Did not found any URL that has more than ${urlThreshHold} requests in the last ${offset}</p>\n"
                        }
                    }
                    htmlDataReplace += "</div>"
                    ddosHtml = badIpHtml.replace("%%%DATA%%%",htmlDataReplace).replace("%TIME%",timeStamp)
                    
                    writeFile(file: 'appddos.html', text: ddosHtml)

                }
            }
        }

            stage ('Create index.html + Push all htmls to WebApp'){
                steps {
                    script {
                        writeFile(file: 'index.html', text: indexHTML)
                        subscription = azure.getSubscriptionIdByFarm("DEVELOP")
                        try {
                            azure.executeAzureCliCommand_New("az webapp up -n DevopsMonitoring -p DevOpsMonitoring --html","DEVELOP")
                        }catch (Exception x) {
                        def msg = "Error cannot upload HTML to webapp : ${x}"
                        kit.Error_Msg(msg)
                        error msg
                        }
                    }
                }
            }
        
        }

    
  


    post {
        success {
            script {
                kit.Info_Msg("BUILD SUCCESS")
            }
        }
        failure {
            script {
                 kit.Error_Msg("BUILD FAILED: PLEASE CHECK ERRORS IN BUILD OUTPUT")
            }
        }
        unstable {
            script {
                 kit.Error_Msg("BUILD UNSTABLE: PLEASE CHECK ERRORS IN BUILD OUTPUT")
            }
        }
        cleanup {
            cleanWs()
        }
    }
}

def Get_Current_Date(){
    def date = new Date()
    sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    return sdf.format(date)
}


def GetLocation(ip){
    maxTry = 3
    numOfTries = 0
    answer = null
    
    while (!answer || numOfTries < maxTry){
        Random rnd = new Random()
        rndNum = rnd.nextInt(3) // random integer in the range of 0, 3  (so one of 0,1, 2)
    
        switch (rndNum){
            case "0":
            kit.Info_Msg("im in case ${rndNum}")
                //https://ipwhois.app/json/8.8.4.4
                answer = kit.Command_Execution_Sh_New ("""curl --max-time 5 https://ipwhois.app/json/${ip} | jq -r '.country' """)
                break;
            case "1":
                kit.Info_Msg("im in case ${rndNum}")
                //https://ipapi.co/8.8.8.8/json/
                answer = kit.Command_Execution_Sh_New ("""curl --max-time 5 https://ipapi.co/${ip}/json/ | jq -r '.country' """)
                break;

            case "2":
                kit.Info_Msg("im in case ${rndNum}")
                //https://ip-api.com/json/8.8.8.8
                answer = kit.Command_Execution_Sh_New ("""curl --max-time 5 http://ip-api.com/json/${ip} | jq -r '.country' """)
                break;
        }                                
        numOfTries++         
    }
    switch (answer){
        case "US":
            answer = "United States"
            break;
        case "IE":
            answer = "Ireland"
            break;
        case "DE":
            answer = "Germany"
            break;
        case "GB":
            answer = "United Kingdom"
            break;
        case "FR":
            answer = "France"
            break;
        case "AU":
            answer = "Australia"
            break;
        case "MX":
            answer = "Mexico"
            break;
        case "SG":
            answer = "Singapore"
            break;
        case "FI":
            answer = "Finland"
            break;
        case "PL":
            answer = "Poland"
            break;
        case "NL":
            answer = "Netherlands"
            break;
        case "RU":
            answer = "Russia"
            break;
        case "IL":
            answer = "Israel"
            break;
    }

    return answer
}