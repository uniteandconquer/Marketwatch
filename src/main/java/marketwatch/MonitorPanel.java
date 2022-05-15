package marketwatch;

import customized.NodeTreeCellRenderer;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

public class MonitorPanel extends javax.swing.JPanel
{
    private GUI gui;
    private DefaultTreeModel monitorTreeModel;
    private JSONObject jSONObject;
    private JSONArray jSONArray;
    protected Timer timer;
    private final int tick = 1000;
    private int nodeInfoUpdateDelta = 60;
    private int currentTick;
    private long lastOnlineTime;
    private long lastPingTime;
    private final SystemInfo systemInfo;
    private final List<NetworkIF> interfaces; 
    private long totalBytesSent = 0;
    private long totalBytesReceived = 0;   
    private long lastBytesSent;
    private long lastBytesReceived;
    private long lastUpdateTime;
    protected long startTime;
    private String nodeStatusString;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private int myBlockHeight;
    private long syncTime;
    protected boolean isSynced;
    private long syncStartTime;
    private int syncStartBlock;
    private boolean coreOnline;
    private boolean priceButtonReady = true;
    private boolean multiSelected;
    protected String blockChainFolder;
    protected String dataFolder;
    private boolean mintingChecked = false;
    private int mintingAccount = 0;
    private final int[] levels = { 0, 7200, 72000 , 201600 , 374400 ,618400 , 
        964000 , 1482400 , 2173600 , 3037600 , 4074400 };

    public MonitorPanel()
    {
        initComponents();
        
        systemInfo = new SystemInfo();
        interfaces = systemInfo.getHardware().getNetworkIFs();

        for (NetworkIF nif : interfaces)
        {
            nif.updateAttributes();
            lastBytesSent += nif.getBytesSent();
            lastBytesReceived += nif.getBytesRecv();
        }     
    }
    
    protected void Initialise(GUI gui)
    { 
        this.gui = gui;
        
        monitorTree.addTreeSelectionListener((TreeSelectionEvent e) ->
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) monitorTree.getLastSelectedPathComponent();
            
            if (node == null)
                return;
            
            if(node.getDepth() == 1)
            {
                if(multiSelected)
                {
                    monitorTree.setSelectionPaths(null);
                    multiSelected = false;
                }
                else
                {
                    TreePath[] selectionPaths = new TreePath[node.getChildCount() + 1];
                    selectionPaths[selectionPaths.length - 1] = new TreePath(node.getPath());
                    for(int i = 0;i < node.getChildCount(); i++)
                    {
                        DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
                        selectionPaths[i] = new TreePath(child.getPath());
                    }
                    monitorTree.setSelectionPaths(selectionPaths);      
                    multiSelected = true;
                }
            }
        });
    }
    
    private void SetBlockChainFolder()
    {
        File propertiesFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(propertiesFile.exists())
        {
            try
            {
                String jsonString = Files.readString(propertiesFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    
                    String folderString = jsonObject.optString("qortalFolder");
                    File qortalDir = new File(folderString);
                    if(!folderString.isBlank() && qortalDir.isDirectory())
                    {
                        blockChainFolder = folderString + "/db";
                        dataFolder = folderString + "/data";
                    }                    
                }
                
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }
    
    private String[] getMintingInfoStrings(String mintingAccount,int blocksMinted,int level,int mintedAdjustment)
    {
        File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(settingsFile.exists())
        {
            try
            {
                String jsonString = Files.readString(settingsFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString); 
                    
                    boolean newAccountFound = true;
                    
                    int blocksLeft = level < 10 ? levels[level + 1] - (blocksMinted + mintedAdjustment) : 0;
                    
                    String mintedSessionString = "Not enough blocks minted data, please wait...";
                    String mintingRateString = "Not enough blocks minted data, please wait...";
                    String levellingString = level < 10 ? "Not enough blocks minted data, please wait..." : "Max level reached";
                    String blocksLeftString = level < 10 ? 
                            String.format("Blocks left till level %d: %s", (level + 1), Utilities.numberFormat(blocksLeft)) :
                            "Max level reached";
                    
                    if(jsonObject.has("mintingAccount"))
                    {
                        if(jsonObject.getString("mintingAccount").equals(mintingAccount))
                        {                                   
                            newAccountFound = false;     
                            
                            jsonObject.put("blocksMintedEnd", blocksMinted);
                            jsonObject.put("snapshotEnd", System.currentTimeMillis());     
                            
                            long duration = jsonObject.getLong("snapshotEnd") - jsonObject.getLong("snapshotStart");
                            int blocksThisSession = jsonObject.getInt("blocksMintedEnd") - jsonObject.getInt("blocksMintedStart");
                            
                            if(blocksThisSession >= 10)
                            {
                                mintedSessionString = String.format(
                                        "%s blocks minted in %s ( since first snapshot on %s )",
                                        Utilities.numberFormat(blocksThisSession), 
                                        Utilities.MillisToDayHrMinMinter(duration),
                                        Utilities.DateFormatShort(jsonObject.getLong("snapshotStart")));
                                
                                double hoursPassed = ((double)duration / 3600000);

                                double bph = ((double) blocksThisSession / hoursPassed);
                                mintingRateString = String.format("Minting rate: %.2f blocks per hour ( %,d blocks per day )", bph,((int)bph * 24));

                                 if(level < 10)
                                 {
                                     double hoursLeft = ((double) blocksLeft / (int) bph);
                                     double millisecLeft = hoursLeft * 3600000;
                                     long timestampLevelUp = System.currentTimeMillis() + (long) millisecLeft;

                                     levellingString = String.format(
                                             "Estimated to reach level %d in %s ( %s )",
                                             (level + 1),
                                             Utilities.MillisToDayHrMinMinter((long) millisecLeft),
                                             Utilities.DateFormatShort(timestampLevelUp));
                                 }
                                 else
                                 {
                                     levellingString = "Maximum level reached";
                                     blocksLeftString = "Maximum level reached";
                                 }                                
                            }
                            else
                            {
                                mintedSessionString = "Not enough blocks minted data for estimation, please wait...";
                                mintingRateString = "First snapshot was " + Utilities.MillisToDayHrMinMinter(duration) +
                                        " ago ( at " + Utilities.DateFormatShort(jsonObject.getLong("snapshotStart")) + " )";
                                levellingString = "Blocks minted this session: " + blocksThisSession + " ( minimum of 10 needed for estimation)";                                
                            }
                                
                        }
                        else
                            newAccountFound = true;
                    }
                    
                    if(newAccountFound)
                    {
                        jsonObject.put("mintingAccount", mintingAccount);
                        jsonObject.put("blocksMintedStart", blocksMinted);
                        jsonObject.put("blocksMintedEnd", blocksMinted);
                        jsonObject.put("snapshotStart", System.currentTimeMillis());
                        jsonObject.put("snapshotEnd", System.currentTimeMillis());
                    }
                    
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
                    {
                        writer.write(jsonObject.toString(1));
                        writer.close();
                    } 
                    
                    if(newAccountFound)
                        return null;
                    else
                        return new String[]{mintedSessionString,mintingRateString,levellingString,blocksLeftString};
                    
                }
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
        
        return null;
    }
    
    private DefaultMutableTreeNode root,statusRoot, statusNode,nodeNode,peers,
            mintingNode,dataNode,uptimeNode,syncNode,blockheightNode,chainHeighNode,
            buildversionNode,blockchainNode,QDN_node,spaceLeftNode,peersNode,allMintersNode,
            knownPeersNode,mintingAccountNode,blocksMintedNode,balanceNode,levelNode,
            mintedSessionNode,mintingRateNode,levellingNode,blocksLeftNode,
            dataUsageNode,averageRateNode,minuteRateNode,hourRateNode,dayRateNode,
            pricesNode,qortToUsdNode,usdToQortNode,qortToLtcNode,ltcToQortNode,
            qortToDogeNode,dogeToQortNode,qortToRavenNode,ravenToQortNode,
            qortToDigibyteNode,digibyteToQortNode;    
    
    protected void CreateMonitorTree()
    {
        SetBlockChainFolder();
        
        monitorTreeModel = (DefaultTreeModel) monitorTree.getModel(); 
        root = (DefaultMutableTreeNode) monitorTreeModel.getRoot();
        
        statusRoot = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("coreStatus"),"status.png"));
        statusNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("statusDefault"),"dot.png"));
        statusRoot.add(statusNode);
        root.add(statusRoot);
        
        nodeNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("node"),"node.png"));
        root.add(nodeNode);        
        syncNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("syncStatusDefault"),"dot.png"));
        nodeNode.add(syncNode);
        blockheightNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("nodeHeightDefault"),"dot.png"));
        nodeNode.add(blockheightNode);
        chainHeighNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("chainHeightDefault"),"dot.png"));
        nodeNode.add(chainHeighNode);     
        if(blockChainFolder != null)
        {            
            blockchainNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("chainSizeDefault"),"dot.png"));
            nodeNode.add(blockchainNode);
            if(dataFolder != null)
            {            
                QDN_node = new DefaultMutableTreeNode(new NodeInfo("QDN data size","dot.png"));
                nodeNode.add(QDN_node);
            }
            spaceLeftNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("spaceLeftDefault"),"dot.png"));
            nodeNode.add(spaceLeftNode);
        } 
        uptimeNode = new DefaultMutableTreeNode(new NodeInfo("uptimeDefault","dot.png"));
        nodeNode.add(uptimeNode);
        buildversionNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("buildVersionDefault"),"dot.png"));
        nodeNode.add(buildversionNode);        
        
        peers = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("peersDefault"),"peers.png"));
        root.add(peers);
        peersNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("peersDefault"),"dot.png"));
        peers.add(peersNode);
        allMintersNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("allMintersDefault"),"dot.png"));
        peers.add(allMintersNode);        
        knownPeersNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("allPeersDefault"),"dot.png"));
        peers.add(knownPeersNode);     
        
        mintingNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("mintingAccNode"),"account.png"));
        root.add(mintingNode);
        mintingAccountNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("mintingAddressDefault"),"dot.png"));
        mintingNode.add(mintingAccountNode);
        blocksMintedNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("blocksMintedDefault"),"dot.png"));
        mintingNode.add(blocksMintedNode);
        balanceNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("balanceDefault"),"dot.png"));
        mintingNode.add(balanceNode);
        levelNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("levelDefault"),"dot.png"));
        mintingNode.add(levelNode);      
        mintedSessionNode = new DefaultMutableTreeNode(new NodeInfo("Blocks minted session info","dot.png"));
        mintingNode.add(mintedSessionNode);
        mintingRateNode = new DefaultMutableTreeNode(new NodeInfo("Minting rate info","dot.png"));
        mintingNode.add(mintingRateNode);
        levellingNode = new DefaultMutableTreeNode(new NodeInfo("Levelling info","dot.png"));
        mintingNode.add(levellingNode);
        blocksLeftNode = new DefaultMutableTreeNode(new NodeInfo("Blocks left till next level info","dot.png"));
        mintingNode.add(blocksLeftNode);
        
        pricesNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("pricesNode"),"prices.png"));  
        root.add(pricesNode);
        qortToUsdNode = new DefaultMutableTreeNode(new NodeInfo("QORT to USD price","dot.png"));
        usdToQortNode = new DefaultMutableTreeNode(new NodeInfo("USD to QORT price","dot.png"));
        qortToLtcNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("q2litePriceDefault"),"dot.png"));
        ltcToQortNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("lite2qPriceDefault"),"dot.png"));
        qortToDogeNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("q2dogePriceDefault"),"dot.png"));
        dogeToQortNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("doge2qPriceDefault"),"dot.png"));
        qortToRavenNode = new DefaultMutableTreeNode(new NodeInfo("QORT to Ravencoin price","dot.png"));
        ravenToQortNode = new DefaultMutableTreeNode(new NodeInfo("Ravencoin to QORT price","dot.png"));
        qortToDigibyteNode = new DefaultMutableTreeNode(new NodeInfo("QORT to Digibyte price","dot.png"));
        digibyteToQortNode = new DefaultMutableTreeNode(new NodeInfo("Digibyte to QORT price","dot.png"));
        pricesNode.add(qortToUsdNode);
        pricesNode.add(usdToQortNode);
        pricesNode.add(qortToLtcNode);
        pricesNode.add(ltcToQortNode);
        pricesNode.add(qortToDogeNode);
        pricesNode.add(dogeToQortNode);
        pricesNode.add(qortToRavenNode);
        pricesNode.add(ravenToQortNode);
        pricesNode.add(qortToDigibyteNode);
        pricesNode.add(digibyteToQortNode);
        
        dataNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("dataUsageNode"),"data.png"));
        root.add(dataNode);
        dataUsageNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("totalUsageDefault"),"dot.png"));
        averageRateNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("avgPerDayDefault"),"dot.png"));
        minuteRateNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("ratePerMinuteDefault"),"dot.png"));
        hourRateNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("ratePerHourDefault"),"dot.png"));
        dayRateNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("ratePerDayDefault"),"dot.png"));
        dataNode.add(dataUsageNode);   
        dataNode.add(averageRateNode);
        dataNode.add(minuteRateNode);
        dataNode.add(hourRateNode);
        dataNode.add(dayRateNode);        
        
        //collapse all but status node on tree creation
        monitorTreeModel.reload();
        gui.ExpandNode(monitorTree, statusRoot, 1);
    }
    
    private void ClearMonitorTree()
    {    
        NodeInfo nodeInfo = (NodeInfo) uptimeNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("uptimeDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(uptimeNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) syncNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("syncStatusDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) blockheightNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("nodeHeightDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(blockheightNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) chainHeighNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("chainHeightDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(chainHeighNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) buildversionNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("buildVersionDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(buildversionNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) peersNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("peersDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(peersNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) allMintersNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("allMintersDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(allMintersNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) knownPeersNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("allPeersDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(knownPeersNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) mintingAccountNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("mintingAddressDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) blocksMintedNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("blocksMintedDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(blocksMintedNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) balanceNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("balanceDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(balanceNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) levelNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("levelDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(levelNode.getPath()),nodeInfo);      
        nodeInfo = (NodeInfo) mintedSessionNode.getUserObject();
        nodeInfo.nodeName = "Blocks minted session info";
        monitorTreeModel.valueForPathChanged(new TreePath(mintedSessionNode.getPath()),nodeInfo);       
        nodeInfo = (NodeInfo) mintingRateNode.getUserObject();
        nodeInfo.nodeName = "Minting rate info";
        monitorTreeModel.valueForPathChanged(new TreePath(mintingRateNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) levellingNode.getUserObject();
        nodeInfo.nodeName = "Levelling info";
        monitorTreeModel.valueForPathChanged(new TreePath(levellingNode.getPath()),nodeInfo); 
        nodeInfo = (NodeInfo) blocksLeftNode.getUserObject();
        nodeInfo.nodeName = "Blocks left till next level info";
        monitorTreeModel.valueForPathChanged(new TreePath(blocksLeftNode.getPath()),nodeInfo);  
        nodeInfo = (NodeInfo) dataUsageNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("totalUsageDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(dataUsageNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) averageRateNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("avgPerDayDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(averageRateNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) minuteRateNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("ratePerMinuteDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(minuteRateNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) hourRateNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("ratePerHourDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(hourRateNode.getPath()),nodeInfo);        
        nodeInfo = (NodeInfo) dayRateNode.getUserObject();
        nodeInfo.nodeName = Main.BUNDLE.getString("ratePerDayDefault");
        monitorTreeModel.valueForPathChanged(new TreePath(dayRateNode.getPath()),nodeInfo);    
        monitorTreeModel.reload();
        gui.ExpandNode(monitorTree, statusRoot, 1);
    }
    
     protected void RestartTimer()
    {              
        //set this variable to avoid showing time left estimate too early
        syncStartTime = System.currentTimeMillis();
        currentTick = 0;
        refreshButton.setEnabled(false); //avoid button spamming

        if (timer == null)
        {
            timer = new Timer();
        }
        else
        {
            timer.cancel();
            timer = new Timer();
        }

        timer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {    
                if (currentTick % nodeInfoUpdateDelta == 0) //refresh every updateDelta
                {   
                    currentTick = 0;
                    refreshButton.setEnabled(false);
                    
                    //This code updates the GUI, but needs to run on a seperate thread due to the delay caused by
                    //ReadStringFromURL, updating swing from a seperate thread causes concurrency issues.
                    //invokeLater() makes sure the changes will occur on the Event Dispatch Tread 
                    SwingUtilities.invokeLater(() ->
                    {
                        RefreshNodeLabels();                     
                    });   
                }
                if (currentTick < 10)
                {
                    refreshButton.setText(Main.BUNDLE.getString("refreshIn") + (10 - currentTick));
                }
                if (currentTick == 10) //allow refresh every 10 seconds
                {
                    refreshButton.setText(Main.BUNDLE.getString("refreshNow"));
                    refreshButton.setEnabled(true);
//                    if(!isSynced)//update every 9 seconds when time approximation is active
//                        RestartTimer();
                }
                
                //show node update status
                if (coreOnline)
                {    
                    String[] split = Main.BUNDLE.getString("pingLabel").split("%%");
                    pingLabel.setText(String.format(split[0] + "%s" + split[1] + "%d" + split[2],  
                                                    Utilities.TimeFormat(lastPingTime),
                                                    (nodeInfoUpdateDelta - (currentTick % nodeInfoUpdateDelta))));
                            
                }
                else
                {
                    lastPingTime = lastPingTime == 0 ? System.currentTimeMillis() : lastPingTime;
                    
                    //show last online (if was online)
                    nodeStatusString = lastOnlineTime == 0 ? Main.BUNDLE.getString("lastRefresh") + Utilities.TimeFormat(lastPingTime)
                            : Main.BUNDLE.getString("lastOnline") + Utilities.DateFormat(lastOnlineTime);
                    
                    String[] split = Main.BUNDLE.getString("nextRefresh").split("%%");
                    nodeStatusString += String.format(split[0] + "%d" + split[1], (nodeInfoUpdateDelta - (currentTick % nodeInfoUpdateDelta)));                   
                    pingLabel.setText(nodeStatusString); 
                }

                currentTick++;
            }
        }, 0, tick);
    }

    private void RefreshNodeLabels()
    {
//        ReadStringFromURL was causing hiccups in GUI timer, using seperate thread
        executor.execute(() ->
        {     
            try
            {                
                //If ReadStringFromURL throws an error, coreOnline will be set to false
                String jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/status");
                
                //only expand tree if status switched from offline to online, expand before setting coreOnline to true
                if(!coreOnline)
                    gui.ExpandTree(monitorTree, 1);
                coreOnline = true;          
                if(priceButtonReady)
                    pricesButton.setEnabled(true);
                
                lastOnlineTime = System.currentTimeMillis();
                
                //First we get all the variables form the Qortal API before we change the nodes in the GUI
                //this due to the time delay for API queries, we want to change the nodes only after all variables are fetched
                int numberOfConnections;
                long uptime;
                String buildVersion;
                int allKnownPeers;
                int mintersOnline;
                String myMintingAddress;
                double myBalance;
                
                //derived from admin/status
                jSONObject = new JSONObject(jsonString);
                myBlockHeight = jSONObject.getInt("height");
                numberOfConnections = jSONObject.getInt("numberOfConnections");
                
                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/info");
                jSONObject = new JSONObject(jsonString);
                uptime = jSONObject.getLong("uptime");
                buildVersion = jSONObject.getString("buildVersion");    

                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/peers/known");
                jSONArray = new JSONArray(jsonString);
                allKnownPeers = jSONArray.length();
                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/online");
                if(jsonString == null)
                    mintersOnline = 0;
                else
                {
                    jSONArray = new JSONArray(jsonString);
                    mintersOnline = jSONArray.length();                      
                }              
                
                jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/mintingaccounts");
                jSONArray = new JSONArray(jsonString);
                //If there's no minting account set we'll get a nullpointer exception
                if(jSONArray.length() > 0)
                {                    
                    if(jSONArray.length() > 1 && !mintingChecked)
                        SetMintingAccount(jSONArray);
                    
                    jSONObject = jSONArray.getJSONObject(mintingAccount);
                    myMintingAddress = jSONObject.getString("mintingAccount");
                    myBalance =  Double.parseDouble(Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/balance/" + myMintingAddress));
                    jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/" + myMintingAddress);
                    jSONObject = new JSONObject(jsonString);
                    
                   NodeInfo nodeInfo = (NodeInfo) mintingAccountNode.getUserObject();
                    nodeInfo.nodeName =Main.BUNDLE.getString("activeAccountDBM") + myMintingAddress;
                    monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),nodeInfo);
                    
                    int blocksMinted =  jSONObject.getInt("blocksMinted");
                    int mintedAdjustment = jSONObject.getInt("blocksMintedAdjustment");
                    nodeInfo = (NodeInfo) blocksMintedNode.getUserObject();
                    nodeInfo.nodeName = String.format(
                            Main.BUNDLE.getString("blocksMintedDBM") + "%s",NumberFormat.getIntegerInstance().format(
                                  blocksMinted  + mintedAdjustment));
                    monitorTreeModel.valueForPathChanged(new TreePath(blocksMintedNode.getPath()),nodeInfo);
                    
                    nodeInfo = (NodeInfo) balanceNode.getUserObject();
                    nodeInfo.nodeName = 
                            Main.BUNDLE.getString("balanceDBM") + myBalance + " QORT";    
                    monitorTreeModel.valueForPathChanged(new TreePath(balanceNode.getPath()),nodeInfo);
                    
                    int level = jSONObject.getInt("level");
                    nodeInfo = (NodeInfo) levelNode.getUserObject();
                    nodeInfo.nodeName = 
                            Main.BUNDLE.getString("levelDBM") + level;
                    monitorTreeModel.valueForPathChanged(new TreePath(levelNode.getPath()),nodeInfo);          
                    
                    String[] mintingInfo = getMintingInfoStrings(myMintingAddress, blocksMinted, level, mintedAdjustment);
                    if(mintingInfo != null)
                    {
                        nodeInfo = (NodeInfo) mintedSessionNode.getUserObject();
                        nodeInfo.nodeName = mintingInfo[0];
                        monitorTreeModel.valueForPathChanged(new TreePath(mintedSessionNode.getPath()),nodeInfo); 
                        
                        nodeInfo = (NodeInfo) mintingRateNode.getUserObject();
                        nodeInfo.nodeName = mintingInfo[1];
                        monitorTreeModel.valueForPathChanged(new TreePath(mintingRateNode.getPath()),nodeInfo); 
                        
                        nodeInfo = (NodeInfo) levellingNode.getUserObject();
                        nodeInfo.nodeName = mintingInfo[2];
                        monitorTreeModel.valueForPathChanged(new TreePath(levellingNode.getPath()),nodeInfo); 
                        
                        nodeInfo = (NodeInfo) blocksLeftNode.getUserObject();
                        nodeInfo.nodeName = mintingInfo[3];
                        monitorTreeModel.valueForPathChanged(new TreePath(blocksLeftNode.getPath()),nodeInfo);                         
                    }                    
                }    
                else
                {           
                    NodeInfo nodeInfo = (NodeInfo) mintingAccountNode.getUserObject();
                    nodeInfo.nodeName =Main.BUNDLE.getString("noAccountDBM");
                    monitorTreeModel.valueForPathChanged(new TreePath(mintingAccountNode.getPath()),nodeInfo); 
                    
                    nodeInfo = (NodeInfo) blocksMintedNode.getUserObject();
                    nodeInfo.nodeName = Main.BUNDLE.getString("blocksMintedDefault");
                    monitorTreeModel.valueForPathChanged(new TreePath(blocksMintedNode.getPath()),nodeInfo);
                    
                    nodeInfo = (NodeInfo) balanceNode.getUserObject();
                    nodeInfo.nodeName = Main.BUNDLE.getString("balanceDefault");    
                    monitorTreeModel.valueForPathChanged(new TreePath(balanceNode.getPath()),nodeInfo);
                    
                    nodeInfo = (NodeInfo) levelNode.getUserObject();
                    nodeInfo.nodeName = Main.BUNDLE.getString("levelDefault");
                    monitorTreeModel.valueForPathChanged(new TreePath(levelNode.getPath()),nodeInfo);                        
                }                
                
                NodeInfo nodeInfo = (NodeInfo) statusNode.getUserObject();
                nodeInfo.nodeName = Main.BUNDLE.getString("coreIsOnline");
                monitorTreeModel.valueForPathChanged(new TreePath(statusNode.getPath()), nodeInfo);
                
                nodeInfo = (NodeInfo) peersNode.getUserObject();
                nodeInfo.nodeName =  
                        Main.BUNDLE.getString("connectedPeers") + numberOfConnections;
                monitorTreeModel.valueForPathChanged(new TreePath(peersNode.getPath()), nodeInfo);
                
                nodeInfo = (NodeInfo) uptimeNode.getUserObject();
                nodeInfo.nodeName = Main.BUNDLE.getString("uptimeTree") + Utilities.MillisToDayHrMin(uptime);                
                monitorTreeModel.valueForPathChanged(new TreePath(uptimeNode.getPath()),nodeInfo);     
                
                nodeInfo = (NodeInfo) buildversionNode.getUserObject();
                nodeInfo.nodeName = Main.BUNDLE.getString("buildVersionTree") + buildVersion;
                monitorTreeModel.valueForPathChanged(new TreePath(buildversionNode.getPath()), nodeInfo);
                
                nodeInfo = (NodeInfo) allMintersNode.getUserObject();
                nodeInfo.nodeName = Main.BUNDLE.getString("mintersOnline") + mintersOnline;
                monitorTreeModel.valueForPathChanged(new TreePath(allMintersNode.getPath()),nodeInfo);
                
                nodeInfo = (NodeInfo) knownPeersNode.getUserObject();
                nodeInfo.nodeName = Main.BUNDLE.getString("allKnownPeers") + allKnownPeers;
                monitorTreeModel.valueForPathChanged(new TreePath(knownPeersNode.getPath()),nodeInfo);

                int chainHeight = Utilities.FindChainHeight();

                if (myBlockHeight < chainHeight)
                {
                    if (isSynced)
                    {
                        syncStartTime = System.currentTimeMillis();
                        syncStartBlock = myBlockHeight;
                        isSynced = false;
                        nodeInfoUpdateDelta = 60;
                    }
                    nodeInfo = (NodeInfo) syncNode.getUserObject();
                    nodeInfo.nodeName = Main.BUNDLE.getString("isSyncing");
                    monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),nodeInfo);   
                }
                else
                {
                    if (!isSynced)
                    {
                        isSynced = true;
                        nodeInfoUpdateDelta = 60;
                        nodeInfo = (NodeInfo) syncNode.getUserObject();
                        nodeInfo.nodeName = Main.BUNDLE.getString("isSynced");
                        monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),nodeInfo);   
                    }
                }

                String heightString = chainHeight == 0 ? "N/A" : String.format("%s", NumberFormat.getIntegerInstance().format(chainHeight));// String.valueOf(chainHeight);
                if(heightString.equals("N/A"))
                {
                    nodeInfo = (NodeInfo) syncNode.getUserObject();
                    nodeInfo.nodeName = Main.BUNDLE.getString("noChainHeight");
                    monitorTreeModel.valueForPathChanged(new TreePath(syncNode.getPath()),nodeInfo);   
                }
                            
                if (myBlockHeight - syncStartBlock > 0)
                {
                    syncTime = ((System.currentTimeMillis() - syncStartTime) / (myBlockHeight - syncStartBlock)) * (chainHeight - myBlockHeight);
                }
                
                //FOR DEBUGGING SYNCTIME
//                System.out.println("ST = " + syncTime + " SSB = " + syncStartBlock + " , MBH = " + myBlockHeight);                
                
                //we want to start time left estimation only when 30 seconds or more have passed since we went out of sync
                //we need to give the algo some time to get a good estimate of blocks_synced per delta time, otherwise the figure
                //will be irrelevant and confusing to the user
                String estimateString = System.currentTimeMillis() - syncStartTime > 30000 ?
                        Main.BUNDLE.getString("estimatedTime") + Utilities.MillisToDayHrMinSec(syncTime) : Main.BUNDLE.getString("estimatingTime");
                //fail safe 
                estimateString = syncTime < 1000 ? Main.BUNDLE.getString("estimatingTime") : estimateString;

                 String blocksString = myBlockHeight < chainHeight
                        ? String.format(Main.BUNDLE.getString("nodeHeight") + "%s", NumberFormat.getIntegerInstance().format(myBlockHeight)) + "  |  "
                        + Main.BUNDLE.getString("blocksLeft") + NumberFormat.getIntegerInstance().format(chainHeight - myBlockHeight)  + " " + estimateString 
                        : String.format(Main.BUNDLE.getString("nodeHeight") + "%s",NumberFormat.getIntegerInstance().format(myBlockHeight));                   

                nodeInfo = (NodeInfo) blockheightNode.getUserObject();
                nodeInfo.nodeName = blocksString;
                monitorTreeModel.valueForPathChanged(new TreePath(blockheightNode.getPath()),nodeInfo);  
                
                nodeInfo = (NodeInfo) chainHeighNode.getUserObject();
                nodeInfo.nodeName = Main.BUNDLE.getString("chainHeight") + heightString;
                monitorTreeModel.valueForPathChanged(new TreePath(chainHeighNode.getPath()),nodeInfo);      
                    
                //must be set after synctime approximation
                lastPingTime = System.currentTimeMillis();                     

                RefreshDataNode();   

                //Using model.nodeChanged was causing arrayIndexOutOfBounds error for jTree, especially on the Pi4 (slower?)
                //Using model.valueForPathChanged seems to have solved this problem
                if(blockChainFolder != null)
                {
                    File folder = new File(blockChainFolder);
                    long size = Utilities.getDirectorySize(folder);
                    
                    nodeInfo = (NodeInfo) blockchainNode.getUserObject();
                    nodeInfo.nodeName = 
                            String.format(Main.BUNDLE.getString("blockChainSizeDBM") + "%sMb",
                                NumberFormat.getIntegerInstance().format(size / 1000000));
                    monitorTreeModel.valueForPathChanged(new TreePath(blockchainNode.getPath()), nodeInfo);   
                    
                    nodeInfo = (NodeInfo) spaceLeftNode.getUserObject();
                    nodeInfo.nodeName =  
                            String.format(Main.BUNDLE.getString("spaceLeftDBM") + "%sMb",
                                NumberFormat.getIntegerInstance().format(folder.getFreeSpace() / 1000000));
                    monitorTreeModel.valueForPathChanged(new TreePath(spaceLeftNode.getPath()),nodeInfo);     
                    
                    if(dataFolder != null)
                    {
                        folder = new File(dataFolder);
                        size = Utilities.getDirectorySize(folder);
                        
                        nodeInfo = (NodeInfo) QDN_node.getUserObject();
                        nodeInfo.nodeName = String.format("QDN data size: %sMb",
                                    NumberFormat.getIntegerInstance().format(size / 1000000));
                        monitorTreeModel.valueForPathChanged(new TreePath(QDN_node.getPath()), nodeInfo);
                    }           
                }               
            }
            catch(ConnectException e)
            {
                coreOnline = false;
                pricesButton.setEnabled(false);
                NodeInfo nodeInfo = (NodeInfo) statusNode.getUserObject();
                nodeInfo.nodeName = Utilities.AllignCenterHTML(Main.BUNDLE.getString("cannotConnectMp"));
                monitorTreeModel.valueForPathChanged(new TreePath(statusNode.getPath()),nodeInfo);   
                ClearMonitorTree();
            }
            catch (IOException | NumberFormatException | TimeoutException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }  
            
            //model.reload(node) was causing array index out of bounds error, select de-select will also reload the node
            TreePath selected = monitorTree.getSelectionPath();
            monitorTree.setSelectionInterval(0, monitorTree.getRowCount() - 1);
            monitorTree.setSelectionPath(selected);
            
        });   //end executor    
    }
    
    private void SetMintingAccount(JSONArray jsonArray)
    {
        mintingChecked = true;
        
        String[] accounts = new String[jsonArray.length()];
        for(int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jso = jsonArray.getJSONObject(i);
            accounts[i] = jso.getString("mintingAccount");
        }        
        
        String choice = (String)JOptionPane.showInputDialog(this, 
                Utilities.AllignCenterHTML(Main.BUNDLE.getString("multipleAccounts")),
                Main.BUNDLE.getString("multipleAccountTitle"), JOptionPane.QUESTION_MESSAGE, null, accounts, accounts[0]); 
        
        for(int i = 0; i < jsonArray.length(); i++)
        {
            JSONObject jso = jsonArray.getJSONObject(i);
            if(jso.getString("mintingAccount").equals(choice))
            {
                mintingAccount = i;     
                break;
            }
        }        
    }
    
    private void RefreshDataNode()
    {       
        long currentBytesSent = 0;
        long currentBytesReceived = 0;

        for (NetworkIF nif : interfaces)
        {
            nif.updateAttributes();
            currentBytesSent += nif.getBytesSent();
            currentBytesReceived += nif.getBytesRecv();
        }

        //FOR DEBUGGING
//        if(currentBytesReceived < lastBytesReceived || currentBytesSent < lastBytesSent)
//            System.out.println(String.format("INCONGRUENT DATA: cbs = %.2f , lbs = %.2f , cbr = %.2f , lbr = %.2f",
//                    (double)currentBytesSent/1000000,(double)lastBytesSent/1000000, (double)currentBytesReceived/1000000,(double)lastBytesReceived/1000000));
        
        
        //Current bytes sent should always be bigger than lastbytes sent
        //If, for some reason Oshi returns a faulty value for getBytes this would result in a negative value for 
        //bytesSent/Rec, which would result in a negative value for totalBytesSent/Rec as well as averageBytesSent/Rec
        long bytesSent = currentBytesSent > lastBytesSent ? currentBytesSent - lastBytesSent : lastBytesSent;
        long bytesReceived = currentBytesReceived > lastBytesReceived ? currentBytesReceived - lastBytesReceived : lastBytesReceived;
        totalBytesSent += bytesSent;
        totalBytesReceived += bytesReceived;
        
        //FOR DEBUGGING
//        System.out.println(String.format("bs = %.2f , br = %.2f , tbs = %.2f , tbr = %.2f , cbs = %.2f , cbr = %.2f , lbs = %.2f , lbr = %.2f", 
//                (double)bytesSent/1000000, (double)bytesReceived/1000000, (double)totalBytesSent/1000000, (double)totalBytesReceived/1000000,
//                 (double)currentBytesSent/1000000, (double)currentBytesReceived/1000000,(double)lastBytesSent/1000000,(double)lastBytesReceived/1000000));

        lastBytesSent = currentBytesSent;
        lastBytesReceived = currentBytesReceived;

        if (lastUpdateTime > 0)//cannot calculate rate on first update (will cause / by 0)
        {
            long timePassed = System.currentTimeMillis() - lastUpdateTime;
            timePassed = timePassed < 1000 ? 1000 : timePassed;//failsafe for / by 0
            long receivedPerSec = bytesReceived / (timePassed / 1000);
            long sentPerSec = bytesSent / (timePassed / 1000);
        
            long averageReceived = (totalBytesReceived / (System.currentTimeMillis() - startTime)) * 86400000;
            long averageSent = (totalBytesSent / (System.currentTimeMillis() - startTime)) * 86400000;

            String[] split = Main.BUNDLE.getString("totalUsage").split("%%");
            
            NodeInfo nodeInfo = (NodeInfo) dataUsageNode.getUserObject();
            nodeInfo.nodeName = String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) totalBytesReceived / 1000000), ((double) totalBytesSent / 1000000));
            monitorTreeModel.valueForPathChanged(new TreePath(dataUsageNode.getPath()),nodeInfo);
            
            split = Main.BUNDLE.getString("avgPerDay").split("%%");
            nodeInfo = (NodeInfo) averageRateNode.getUserObject();
            nodeInfo.nodeName = String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double)  averageReceived / 1000000), ((double) averageSent / 1000000));
            monitorTreeModel.valueForPathChanged(new TreePath(averageRateNode.getPath()),nodeInfo);

            split = Main.BUNDLE.getString("ratePerMinute").split("%%");
            nodeInfo = (NodeInfo) minuteRateNode.getUserObject();
            nodeInfo.nodeName = String.format( split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) (receivedPerSec * 60) / 1000000), ((double) (sentPerSec * 60) / 1000000));
            monitorTreeModel.valueForPathChanged(new TreePath(minuteRateNode.getPath()),nodeInfo);

            split = Main.BUNDLE.getString("ratePerHour").split("%%");
            nodeInfo = (NodeInfo) hourRateNode.getUserObject();
            nodeInfo.nodeName = String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) (receivedPerSec * 3600) / 1000000), ((double) (sentPerSec * 3600) / 1000000));
            monitorTreeModel.valueForPathChanged(new TreePath(hourRateNode.getPath()),nodeInfo);

            split = Main.BUNDLE.getString("ratePerDay").split("%%");
            nodeInfo = (NodeInfo) dayRateNode.getUserObject();
            nodeInfo.nodeName = String.format(split[0] + "%.2f" + split[1] + "%.2f" + split[2],
                    ((double) (receivedPerSec * 86400) / 1000000), ((double) (sentPerSec * 86400) / 1000000));
            monitorTreeModel.valueForPathChanged(new TreePath(dayRateNode.getPath()),nodeInfo);
        }
        
        lastUpdateTime = System.currentTimeMillis();           
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        monitorPanel = new javax.swing.JPanel();
        monitorTreeScrollPane = new javax.swing.JScrollPane();
        monitorTree = new javax.swing.JTree();
        monitorTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        //disable selection bar for Nimbus L&F       
        UIDefaults paneDefaults = new UIDefaults();
        paneDefaults.put("Tree.selectionBackground",null);
        monitorTree.putClientProperty("Nimbus.Overrides",paneDefaults);
        monitorTree.putClientProperty("Nimbus.Overrides.InheritDefaults",false);

        refreshButton = new javax.swing.JButton();
        pingLabel = new javax.swing.JLabel();
        pricesButton = new javax.swing.JButton();
        setQortalFolder = new javax.swing.JButton();
        resetMintingRateBtn = new javax.swing.JButton();

        monitorPanel.setLayout(new java.awt.GridBagLayout());

        monitorTree.setFont(new java.awt.Font("Serif", 0, 13)); // NOI18N
        monitorTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(new NodeInfo("Node Monitor","qortal.png"))));
        monitorTree.setCellRenderer(new NodeTreeCellRenderer());
        monitorTreeScrollPane.setViewportView(monitorTree);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        monitorPanel.add(monitorTreeScrollPane, gridBagConstraints);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        refreshButton.setText(bundle.getString("refreshButtonDefault")); // NOI18N
        refreshButton.setMinimumSize(new java.awt.Dimension(125, 30));
        refreshButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                refreshButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 5, 8, 5);
        monitorPanel.add(refreshButton, gridBagConstraints);

        pingLabel.setText(bundle.getString("pingLabelDefault")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        monitorPanel.add(pingLabel, gridBagConstraints);

        pricesButton.setText(bundle.getString("pricesButton")); // NOI18N
        pricesButton.setEnabled(false);
        pricesButton.setMinimumSize(new java.awt.Dimension(125, 30));
        pricesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                pricesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 10, 8, 5);
        monitorPanel.add(pricesButton, gridBagConstraints);

        setQortalFolder.setText("Set Qortal folder");
        setQortalFolder.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                setQortalFolderActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        monitorPanel.add(setQortalFolder, gridBagConstraints);

        resetMintingRateBtn.setText("Reset minting rate");
        resetMintingRateBtn.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                resetMintingRateBtnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        monitorPanel.add(resetMintingRateBtn, gridBagConstraints);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 719, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(monitorPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 719, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 648, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(monitorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 648, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_refreshButtonActionPerformed
    {//GEN-HEADEREND:event_refreshButtonActionPerformed
        RestartTimer();
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void pricesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_pricesButtonActionPerformed
    {//GEN-HEADEREND:event_pricesButtonActionPerformed
        NodeInfo nodeInfo = (NodeInfo) qortToUsdNode.getUserObject();
        nodeInfo.nodeName = String.format("Fetching QORT to USD price. Please wait...");        
        monitorTreeModel.valueForPathChanged(new TreePath(qortToUsdNode.getPath()),nodeInfo);
        monitorTreeModel.reload(qortToUsdNode);
        
        nodeInfo = (NodeInfo) usdToQortNode.getUserObject();
        nodeInfo.nodeName = String.format("Fetching USD to QORT price. Please wait...");
        monitorTreeModel.valueForPathChanged(new TreePath(usdToQortNode.getPath()),nodeInfo);
        monitorTreeModel.reload(usdToQortNode);
        
        nodeInfo = (NodeInfo) qortToLtcNode.getUserObject();
        nodeInfo.nodeName = String.format(Main.BUNDLE.getString("fetchQ2Lite"));
        monitorTreeModel.valueForPathChanged(new TreePath(qortToLtcNode.getPath()),nodeInfo);
        monitorTreeModel.reload(qortToLtcNode);
        
        nodeInfo = (NodeInfo) ltcToQortNode.getUserObject();
        nodeInfo.nodeName = String.format(Main.BUNDLE.getString("fetchLite2Q"));
        monitorTreeModel.valueForPathChanged(new TreePath(ltcToQortNode.getPath()),nodeInfo);
        monitorTreeModel.reload(ltcToQortNode);
        
        nodeInfo = (NodeInfo) qortToDogeNode.getUserObject();
        nodeInfo.nodeName = String.format(Main.BUNDLE.getString("fetchQ2Doge"));
        monitorTreeModel.valueForPathChanged(new TreePath(qortToDogeNode.getPath()),nodeInfo);
        monitorTreeModel.reload(qortToDogeNode);  
        
        nodeInfo = (NodeInfo) dogeToQortNode.getUserObject();
        nodeInfo.nodeName = String.format(Main.BUNDLE.getString("fetchDoge2Q"));
        monitorTreeModel.valueForPathChanged(new TreePath(dogeToQortNode.getPath()),nodeInfo);
        monitorTreeModel.reload(dogeToQortNode);   
        
        nodeInfo = (NodeInfo) qortToRavenNode.getUserObject();
        nodeInfo.nodeName = String.format("Fetching QORT to Ravencoin price. Please wait...");        
        monitorTreeModel.valueForPathChanged(new TreePath(qortToRavenNode.getPath()),nodeInfo);
        monitorTreeModel.reload(qortToRavenNode);
        
        nodeInfo = (NodeInfo) ravenToQortNode.getUserObject();
        nodeInfo.nodeName = String.format("Fetching Ravencoin to QORT price. Please wait...");
        monitorTreeModel.valueForPathChanged(new TreePath(ravenToQortNode.getPath()),nodeInfo);
        monitorTreeModel.reload(ravenToQortNode);
        
        nodeInfo = (NodeInfo) qortToDigibyteNode.getUserObject();
        nodeInfo.nodeName = String.format("Fetching QORT to Digibyte price. Please wait...");        
        monitorTreeModel.valueForPathChanged(new TreePath(qortToDigibyteNode.getPath()),nodeInfo);
        monitorTreeModel.reload(qortToDigibyteNode);
        
        nodeInfo = (NodeInfo) digibyteToQortNode.getUserObject();
        nodeInfo.nodeName = String.format("Fetching Digibyte to QORT price. Please wait...");
        monitorTreeModel.valueForPathChanged(new TreePath(digibyteToQortNode.getPath()),nodeInfo);
        monitorTreeModel.reload(digibyteToQortNode);
        
        pricesButton.setEnabled(false);
        priceButtonReady = false;
        Timer buttonTimer = new Timer();
        TimerTask buttonTask = new TimerTask()
        {
            @Override
            public void run()
            {
                priceButtonReady = true;
                pricesButton.setEnabled(true);
            }
        };
        buttonTimer.schedule(buttonTask, 120000);
        
        //Pinging for prices causes a delay, jamming the GUI, using a sepertate thread
        Thread thread = new Thread(() ->
        {
            try
            {
                 long now = Instant.now().getEpochSecond();
            
                String jsonString = Utilities.ReadStringFromURL(
                        "https://poloniex.com/public?command=returnChartData&currencyPair=USDC_LTC&start="
                        + (now - 3000) + "&end=9999999999&resolution=auto");
                JSONArray pricesArray = new JSONArray(jsonString);
                JSONObject lastObject = pricesArray.getJSONObject(pricesArray.length() - 1);
                double LTC_USDprice = 0;
                //will be 0 if result is invalid
                if (lastObject.getLong("date") > 0)
                    LTC_USDprice = (double) lastObject.getDouble("close");                
                
                 jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/crosschain/price/LITECOIN?maxtrades=10");
                 double LTCprice = ((double)Long.parseLong(jsonString) / 100000000);
                 jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/crosschain/price/DOGECOIN?maxtrades=10");
                 double dogePrice = ((double) Long.parseLong(jsonString) / 100000000);
                 jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/crosschain/price/RAVENCOIN?maxtrades=10");
                 double ravenPrice = ((double) Long.parseLong(jsonString) / 100000000);
                 jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/crosschain/price/DIGIBYTE?maxtrades=10");
                 double digibytePrice = ((double) Long.parseLong(jsonString) / 100000000);
                
                double qortUsdPrice = LTC_USDprice * (1 / LTCprice);
                 
                 //update swing components in EDT
                 SwingUtilities.invokeLater(() ->
                 {
                    NodeInfo ni = (NodeInfo) qortToUsdNode.getUserObject();
                    ni.nodeName = String.format("1 QORT = %.5f USD", ((double)qortUsdPrice));
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToUsdNode.getPath()),ni);
                    monitorTreeModel.reload(qortToUsdNode);
                    
                    ni = (NodeInfo) usdToQortNode.getUserObject();
                    ni.nodeName = String.format("1 USD = %.5f QORT", 1 / qortUsdPrice);
                    monitorTreeModel.valueForPathChanged(new TreePath(usdToQortNode.getPath()),ni);
                    monitorTreeModel.reload(usdToQortNode);
                    
                    ni = (NodeInfo) qortToLtcNode.getUserObject();
                    ni.nodeName = String.format("1 QORT = %.5f Litecoin", ((double)1/LTCprice));
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToLtcNode.getPath()),ni);
                    monitorTreeModel.reload(qortToLtcNode);
                    
                    ni = (NodeInfo) ltcToQortNode.getUserObject();
                    ni.nodeName = String.format("1 Litecoin = %.5f QORT", LTCprice);
                    monitorTreeModel.valueForPathChanged(new TreePath(ltcToQortNode.getPath()),ni);
                    monitorTreeModel.reload(ltcToQortNode);
                    
                    ni = (NodeInfo) qortToDogeNode.getUserObject();
                    ni.nodeName =  String.format("1 QORT = %.5f Dogecoin", ((double) 1/dogePrice));
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToDogeNode.getPath()),ni);
                    monitorTreeModel.reload(qortToDogeNode);      
                    
                    ni = (NodeInfo) dogeToQortNode.getUserObject();
                    ni.nodeName = String.format("1 Dogecoin = %.5f QORT", dogePrice);
                    monitorTreeModel.valueForPathChanged(new TreePath(dogeToQortNode.getPath()),ni);
                    monitorTreeModel.reload(dogeToQortNode);        
                    
                    ni = (NodeInfo) qortToRavenNode.getUserObject();
                    ni.nodeName =  String.format("1 QORT = %.5f Ravencoin", ((double) 1/ravenPrice));
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToRavenNode.getPath()),ni);
                    monitorTreeModel.reload(qortToRavenNode);      
                    
                    ni = (NodeInfo) ravenToQortNode.getUserObject();
                    ni.nodeName = String.format("1 Ravencoin = %.5f QORT", ravenPrice);
                    monitorTreeModel.valueForPathChanged(new TreePath(ravenToQortNode.getPath()),ni);
                    monitorTreeModel.reload(ravenToQortNode);    
                    
                    ni = (NodeInfo) qortToDigibyteNode.getUserObject();
                    ni.nodeName =  String.format("1 QORT = %.5f Digibyte", ((double) 1/digibytePrice));
                    monitorTreeModel.valueForPathChanged(new TreePath(qortToDigibyteNode.getPath()),ni);
                    monitorTreeModel.reload(qortToDigibyteNode);      
                    
                    ni = (NodeInfo) digibyteToQortNode.getUserObject();
                    ni.nodeName = String.format("1 Digibyte = %.5f QORT", digibytePrice);
                    monitorTreeModel.valueForPathChanged(new TreePath(digibyteToQortNode.getPath()),ni);
                    monitorTreeModel.reload(digibyteToQortNode);             
                 });
            }
            catch (IOException | NumberFormatException | TimeoutException e)
            {
                BackgroundService.AppendLog(e);
            }
        });
        thread.start();
    }//GEN-LAST:event_pricesButtonActionPerformed

    private void setQortalFolderActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_setQortalFolderActionPerformed
    {//GEN-HEADEREND:event_setQortalFolderActionPerformed
        JOptionPane.showMessageDialog(this, Utilities.AllignCenterHTML(
                    "If you're running this app on the same system as your<br/>"
                + " Qortal core you can set the location of the Qortal folder<br/>"
                + " to monitor the size of your blockchain and QDN data<br/><br/>"
                + "Please select the main 'qortal' folder in the file chooser menu"));
        
        JFileChooser jfc = new JFileChooser(System.getProperty("user.home"));
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = jfc.showSaveDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {            
            File selectedDirectory = jfc.getSelectedFile();   
            if(!selectedDirectory.getName().equals("qortal"))
            {
                JOptionPane.showMessageDialog(this, Utilities.AllignCenterHTML("The directory should be named 'qortal' "
                        + "<br/>Qortal directory was not set"));
                return;
            }
            File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
            if(settingsFile.exists())
            {
                try
                {
                    String jsonString = Files.readString(settingsFile.toPath());
                    if(jsonString != null)
                    {
                        JSONObject jsonObject = new JSONObject(jsonString);
                        jsonObject.put("qortalFolder",selectedDirectory.getPath());
                        
                        File blockchainDir = new File(selectedDirectory.getPath() + "/db");
                        if(!blockchainDir.isDirectory())
                            blockchainDir.mkdir();
                        blockChainFolder = blockchainDir.getPath();
                        File dataDir = new File(selectedDirectory.getPath() + "/data");
                        if(!dataDir.isDirectory())
                            dataDir.mkdir();
                        dataFolder = dataDir.getPath();
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile))) 
                        {
                            writer.write(jsonObject.toString(1));
                        }                            
                        if(blockchainNode == null)
                        {                            
                            blockchainNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("chainSizeDefault"),"dot.png"));
                            nodeNode.add(blockchainNode);  
                            QDN_node = new DefaultMutableTreeNode(new NodeInfo("QDN data size","dot.png"));
                            nodeNode.add(QDN_node);
                            spaceLeftNode = new DefaultMutableTreeNode(new NodeInfo(Main.BUNDLE.getString("spaceLeftDefault"),"dot.png"));
                            nodeNode.add(spaceLeftNode);
                            monitorTreeModel.nodeStructureChanged(nodeNode);
                            gui.marketPanel.updateGuiItems();
                        }    
                    }
                }
                catch (IOException | JSONException e)
                {
                    BackgroundService.AppendLog(e);
                }
            }
        }
    }//GEN-LAST:event_setQortalFolderActionPerformed

    private void resetMintingRateBtnActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_resetMintingRateBtnActionPerformed
    {//GEN-HEADEREND:event_resetMintingRateBtnActionPerformed
       File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(settingsFile.exists())
        {
            try
            {
                String jsonString = Files.readString(settingsFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString); 
                    jsonObject.remove("mintingAccount");
                    jsonObject.remove("blocksMintedStart");
                    jsonObject.remove("blocksMintedEnd");
                    jsonObject.remove("snapshotStart");
                    jsonObject.remove("snapshotEnd");                    
                    
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
                    {
                        writer.write(jsonObject.toString(1));
                        writer.close();
                    }   
                }                
                }
                catch (IOException | JSONException e)
                {
                    BackgroundService.AppendLog(e);
                }
        }
    }//GEN-LAST:event_resetMintingRateBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel monitorPanel;
    private javax.swing.JTree monitorTree;
    private javax.swing.JScrollPane monitorTreeScrollPane;
    private javax.swing.JLabel pingLabel;
    private javax.swing.JButton pricesButton;
    private javax.swing.JButton refreshButton;
    private javax.swing.JButton resetMintingRateBtn;
    private javax.swing.JButton setQortalFolder;
    // End of variables declaration//GEN-END:variables
}
