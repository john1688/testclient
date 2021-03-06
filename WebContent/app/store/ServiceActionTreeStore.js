Ext.define('MyApp.store.ServiceActionTreeStore', {
	extend: 'Ext.data.TreeStore',
	storeId:"ServiceActionTreeStore",
    root: {
        text: '根节点',
        id: 'root',
        path:'root',
        expanded: true
    }, 
    proxy: {
        type: 'ajax',
        api: {
            read: 'job/getTreeChildNodes'                
        },
        reader: {
            type: 'json',
            messageProperty: 'msg'
        },
        extraParams: {
        	topPath: ''
        }
    }
});
