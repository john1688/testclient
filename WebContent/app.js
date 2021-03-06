/*
 * File: app.js
 *
 * This file was generated by Sencha Architect version 2.2.2.
 * http://www.sencha.com/products/architect/
 *
 * This file requires use of the Ext JS 4.2.x library, under independent license.
 * License of Sencha Architect does not include license for Ext JS 4.2.x. For more
 * details see http://www.sencha.com/license or contact license@sencha.com.
 *
 * This file will be auto-generated each and everytime you save your project.
 *
 * Do NOT hand edit this file.
 */

//@require @packageOverrides
Ext.util.Cookies.set('MobileSvcUrl','http://XXXX/TestOperationService.svc/');

Ext.Loader.setConfig({
    enabled: true
});

Ext.application({
    models: [
	    'CheckPoint',
	    'TestHistory',
	    'TestItem',
	    'BatchRun',
	    'PreConfig',
	    'QueryBoundDataItem',
	    'ServiceBoundDataItem',
	    'SqlVerificationDataItem',
	    'TestCaseResult',
	    'TestStatusDistribution',
	    'TestPassedRate',
	    'RunningSet',
	    'ScheduledRunningSet',
	    'MixActionSetting'
    ],
    stores: [
    	'CheckPoint',
    	'CheckPointResult',
    	'TestHistory',
    	'TestItem',
    	'BatchRun',
    	'PreConfig',
    	'QueryBoundDataItem',
    	'ServiceBoundDataItem',
    	'SqlVerificationDataItem',
    	'TestCaseResult',
    	'TestStatusDistribution',
    	'TestPassedRate',
    	'OutputParameterDataItem',
    	'RunningSet',
    	'SelectedTreeStore',
    	'ScheduledRunningSet',
    	'StandardTreeStore',
    	'FolderStore',
    	'PreServiceTreeStore',
    	'SelectingTreeStore',
    	'ServiceActionTreeStore',
    	'TestSelectionTreeStore',
    	'MixActionSetting'
    ],
    views: [
        'ReportView',
        'Base',
        'AddFolderWindow',
        'AddItemWindow',
        "ModifyNodeWindow",
        "MainPanel",
        "TestConfigWindow",
        "FolderPanel",
        "EnvEditWindow",
        "AddCheckPointWindow",
        "GenerateParametersWindow",
        "TestHistoryWindow",
        'BatchExecutionWindow',
        'BatchTestHistoryWindow',
        'PasswordConfirmationWindow',
        'RootPanel',
        'FolderSelectionWindow',
        'CronExpressionSettingWindow',
        'TestSelectionWindow',
        'PreConfigurationWindow',
        'PreDBQuerySettingWindow',
        'PreServiceSettingWindow',
        'SqlActionSettingWindow',
        'SqlVerificationSettingWindow',
        'FormatSoa1XmlWindow',
        'ShrinkResponseStringWindow',
        'ServiceActionWindow',
        'CopyTestAtSameDirWindow',
        'GenerateCheckpointsWindow',
        'DeleteExpiredHistoryFilesWindow',
        'PasswordConfirmationForHistoryFileClean',
        'OutputParameterSettingWindow',
        'DeletedModeWindow',
        'RunningSetSettingWindow',
        'RunningSetUpdateWindow',
        'RunningSetPanel',
        'TestCaseResultGrid',
        'TestStatusDistributionPieChart',
        'TestPassedRateTrendChart',
        'ScheduledRunningSetWindow',
        'SocketTestConfigWindow',
        'ConvertDatagramWindow',
        'JsExpressionCheckpointSettingWindow',
        'MixActionSettingWindow'
    ],
    autoCreateViewport: true,
    name: 'MyApp'
});

Ext.onReady( 
	function(){
		Ext.getCmp('Base').RootName='root';
 		var url=location.href;
 		if(url.indexOf('#report=')>0 && !new RegExp('#report=$').test(url)){
 			var source=url.substring(url.indexOf('#report=')+8);
 			Ext.getCmp('Body').removeAll(false);
 			Ext.getCmp('Base').add(Ext.widget('ReportView'));
 			Ext.getCmp('Base').folderName=source;
 			Ext.getStore('TestItem').proxy.extraParams.folderName=Ext.getCmp('Base').folderName;
 			Ext.getStore('TestItem').load();
 			//Ext.widget('ReportView').fireEvent('render');
 		}
 		else{
 			if(url.indexOf('#node=')>0 && !new RegExp('#node=$').test(url)){
	 			url=url.substring(url.indexOf('#node=')+6).replace(new RegExp('/','gm'), '>');
	 	 		if(url.substring(url.length-1)=='>')
	 	 			url=url.substring(0, url.length-1);
	 	 		var node=Ext.getStore('StandardTreeStore').getNodeById(url);
	 	 		if(node!=undefined)
		 			Ext.getCmp('tree').expandPath(node.getPath(), 'id','/', function(success, lastNode) {
		 				Ext.getCmp('tree').getSelectionModel().select(node);
		 				Ext.getCmp('tree').fireEvent('itemmousedown',null,node);
		 			});	
 			}
 		} 
});
