package marketwatch;

import enums.Folders;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;

public class TradeExtractor
{
    protected static boolean LOOKUP_HALTED;
    
    public static void extractTrades(MarketPanel marketPanel)
     {  
        Thread thread = new Thread(()->
        {   
            String updateString;
            
            marketPanel.updateInProgress = true;            
            marketPanel.updateStatusLabel.setText("Update in progress");
            
            marketPanel.progressBar.setVisible(true);
            boolean chartsNeedInit = false;
            
            File tradesFile = new File(System.getProperty("user.dir") + "/" + Folders.DB.get() + "/trades.mv.db");
            if (!tradesFile.exists())
                ConnectionDB.CreateDatabase("trades",Folders.DB.get());           
            
            try(Connection connection = ConnectionDB.getConnection("trades",Folders.DB.get()))
            {
                if (!marketPanel.dbManager.TableExists("all_trades", connection))
                {
                    marketPanel.dbManager.CreateTable(new String[]
                    {
                        "all_trades",
                        "timestamp", "long",
                        "blockheight", "int",
                        "amount", "double",
                        "foreign_chain", "varchar(15)",
                        "signature", "varchar(100)",
                        "at_json", "varchar(max)"
                    }, connection);
                    
                    chartsNeedInit = true;
                }
                
                marketPanel.updateButton.setEnabled(false);   
                marketPanel.stopButton.setEnabled(true);
                marketPanel.autoUpdateCheckbox.setEnabled(false);

                marketPanel.lookupStatusLabel.setText(Utilities.AllignCenterHTML(
                        "Fetching all trade transactions from the blockchain<br/>"
                                + "This may take a while depending on the last time since this update was executed<br/><br/>"
                                + "Please wait..."));                
                extractTransactions(marketPanel,connection);
                //only allow stop on extractTransactions, extract trades should never take long enough to warrant 
                //terminating the execution
                marketPanel.stopButton.setEnabled(false);
                
                int[] dogeResults,btcResults,ltcResults;
                
                marketPanel.lookupStatusLabel.setText("Updating Dogecoin trades. Please wait...");
                dogeResults = extractTrades("DOGECOIN",marketPanel,connection);
                marketPanel.lookupStatusLabel.setText("Updating Bitcoin trades. Please wait...");
                btcResults = extractTrades("BITCOIN",marketPanel,connection);
                
                String ltcStatus = "Updating Litecoin trades. Please wait...";
                marketPanel.lookupStatusLabel.setText(ltcStatus);
                ltcResults = extractTrades("LITECOIN",marketPanel,connection);
                
                String statusString = Utilities.AllignCenterHTML(
                        String.format("Update finished<br/><br/>"
                                + "New Dogecoin trades : %d | Total Dogecoin trades : %d<br/>"
                                + "New Bitcoin trades : %d | Total Bitcoin trades : %d<br/>"
                                + "New Litecoin trades : %d | Total Litecoin trades : %d", 
                                dogeResults[0],dogeResults[1],btcResults[0],btcResults[1],ltcResults[0],ltcResults[1]));
                
                //If usd lookup failed, append the status string with the usd failed message
                if(!marketPanel.lookupStatusLabel.getText().equals(ltcStatus))
                    statusString += Utilities.AllignCenterHTML("<br/>" + marketPanel.lookupStatusLabel.getText());
                
                marketPanel.fillTradesTables();
                marketPanel.lookupStatusLabel.setText(statusString);

                LOOKUP_HALTED = false;
                
                updateString = "Last updated : %s | Next update in %d minutes";
                
                if(chartsNeedInit)
                {
                    //This code gets executed if the trades db doesn't have the all_trades table
                    //which will most likely not occur. It's possible that chart init will not work properly 
                    //A restart of the application will fix that
                    marketPanel.initCharts();                    
                    for(int i = 0; i < marketPanel.tabbedPane.getTabCount(); i++)
                        marketPanel.tabbedPane.setEnabledAt(i,true);
                }
                else
                    marketPanel.updateCharts();
            }
            catch (ConnectException e)
            {
                marketPanel.lookupStatusLabel.setText(Utilities.AllignCenterHTML("Could not connect to Qortal core<br/>"
                        + "Make sure your core is online and/or your SHH tunnel is open"));
                BackgroundService.AppendLog(e);
                marketPanel.progressBar.setStringPainted(false);
                marketPanel.stopButton.setEnabled(false);
                LOOKUP_HALTED = false;
                updateString = "Last update attempt : %s | Next attempt in %d minutes";
            }
            catch (Exception e)
            {
                marketPanel.lookupStatusLabel.setText(Utilities.AllignCenterHTML("An unexpected exception occured:<br/>"
                        + e.toString()));
                BackgroundService.AppendLog(e);
                marketPanel.progressBar.setStringPainted(false);
                marketPanel.stopButton.setEnabled(false);
                LOOKUP_HALTED = false;
                updateString = "Last update attempt : %s | Next attempt in %d minutes";
            }
            
            marketPanel.updateInProgress = false;
            
            marketPanel.progressBar.setVisible(false);
            marketPanel.updateButton.setEnabled(true);
            marketPanel.autoUpdateCheckbox.setEnabled(true);
            
            marketPanel.lastUpdatedTime = System.currentTimeMillis();
            marketPanel.updateStatusLabel.setText(String.format(
                    updateString, Utilities.DateFormat(System.currentTimeMillis()), (MarketPanel.UPDATE_INTERVAL - marketPanel.currentTick)));
            
            System.gc();
        });
        thread.start();                
     }    
    
     private static void extractTransactions(MarketPanel marketPanel,Connection connection) throws ConnectException, TimeoutException, IOException
    {        
        DatabaseManager dbManager = marketPanel.dbManager;
        
        marketPanel.progressBar.setValue(0);
        marketPanel.progressBar.setStringPainted(true);
        marketPanel.progressBar.setString("Fetching transactions from blockchain  |  Please wait...");

        //used for progress bar calculation (final int highest derives from this)
        int highestBlock = Utilities.FindChainHeight();
        int totalTxCount = dbManager.getRowCount("all_trades", connection);

        //API call is called starting at last checked block until current chain height
        int offset = 0; //dbManager.getRowCount("all_trades", connection);
        int limit = 100; //txSlider.getValue(); 
        JSONArray txArray;
        JSONObject txObject;
        String jsonString;

        //used to keep the log from displaying errors when empty tx table is created
        boolean tableEmpty = dbManager.GetColumn("all_trades", "blockheight", "", "", connection).isEmpty();
        //used to know when to check the signature and for progress bar calculation  
        final int lastCheckedBlock = tableEmpty ? 0 : (int) dbManager.GetFirstItem("all_trades", "blockheight", "timestamp", "desc", connection);

        //used to break from loop once a the last local tx entry equals current result in API  call
        String lastSignature = tableEmpty ? "" : (String) dbManager.GetFirstItem("all_trades", "signature", "timestamp", "desc", connection);

//        System.err.println("ls = " + lastSignature + " , lcb = " + lastCheckedBlock);

        int totalTxFound = 0;

        long startTime = System.currentTimeMillis();

        do
        {
            if (LOOKUP_HALTED)
                break;

            jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/transactions/search?startBlock=" + (lastCheckedBlock - 1) + "&"
                    + "txType=AT&confirmationStatus=CONFIRMED&limit=" + limit + "&offset=" + offset + "&reverse=false");

            txArray = new JSONArray(jsonString);

            int blockHeight = lastCheckedBlock;

            for (int i = 0; i < txArray.length(); i++)
            {
                txObject = txArray.getJSONObject(i);

                long timestamp = txObject.getLong("timestamp");
                String signature = txObject.getString("signature");
                blockHeight = txObject.getInt("blockHeight");

                if (blockHeight <= lastCheckedBlock)
                {
                    if (signature.equals(lastSignature))
                    {
                        BackgroundService.AppendLog("Existing tx signature found : " + signature);
                        continue;
                    }
                }

                String txRecipient = txObject.getString("recipient");
                String atAddress = txObject.getString("atAddress");

                String atString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/crosschain/trade/" + atAddress);
                JSONObject atAddressObject = new JSONObject(atString);

                String status = atAddressObject.getString("mode");
                String foreignChain = atAddressObject.getString("foreignBlockchain");

                if (status.equals("REDEEMED"))
                {

                    String atRecipient = atAddressObject.getString("qortalPartnerReceivingAddress");
                    //check if this at is a deposit from a purchase
                    if (txRecipient.equals(atRecipient))
                    {
                        //gets the timestamp of the purchase (not deploy timestamp)
                        double amount = txObject.getDouble("amount");

                        dbManager.InsertIntoDB(new String[]
                        {
                            "all_trades",
                            "timestamp", String.valueOf(timestamp),
                            "blockheight", String.valueOf(blockHeight),
                            "amount", String.valueOf(amount),
                            "foreign_chain", Utilities.SingleQuotedString(foreignChain),
                            "signature", Utilities.SingleQuotedString(signature),
                            "at_json", Utilities.SingleQuotedString(atString)
                        }, connection);

                        //can't use txArray lenght, some tx's in array might be skipped
                        totalTxFound++;
                        totalTxCount++;

                    }
                }
            }

            highestBlock = highestBlock == 0 ? blockHeight : highestBlock;
            final int highest = highestBlock;
            final int blocksLeft = highest - blockHeight;
            final int height = blockHeight;
            final int txFound = totalTxFound;
            final int txCount = totalTxCount;

            SwingUtilities.invokeLater(() ->
            {
                int blocksDone = height - lastCheckedBlock;
                long timePassed = System.currentTimeMillis() - startTime;
                double blocksPerMs = ((double) blocksDone / timePassed);
                long timeLeft = (long) (blocksLeft / blocksPerMs);

                double txPerBlock = ((double) txFound / blocksDone);
                int txExpected = (int) (blocksLeft * txPerBlock);

                double percent = ((double) height / highest) * 100; //    (highest - (lastBlockHeight - lastCheckedBlock)) / highest) * 100;
                marketPanel.progressBar.setValue((int) percent);
                marketPanel.progressBar.setString(String.format("%.2f%% done  |  ", percent)
                        + "Estimated time left : " + Utilities.MillisToDayHrMinSec(timeLeft) + "  |  "
                        + "Time passed : " + Utilities.MillisToDayHrMinSec(timePassed));

                marketPanel.lookupStatusLabel.setText(Utilities.AllignCenterHTML("Blocks done : " + Utilities.numberFormat(height) + "  ||  "
                        + "Blocks left : " + Utilities.numberFormat(blocksLeft) + "  ||  "
                        + "Blocks done this session " + Utilities.numberFormat(blocksDone) + "  ||  "
                        + "Last block : " + Utilities.numberFormat(highest) + "<br/><br/>"
                        + "Total TX found : " + Utilities.numberFormat(txCount) + "  ||  "
                        + "TX found this session : " + Utilities.numberFormat(txFound) + " ||  "
                        + "Estimated TX left to find :  " + Utilities.numberFormat(txExpected)));

                dbManager.FillJTableOrder("all_trades", "timestamp", "desc", 250, marketPanel.txTable, connection);
            });

            offset += limit;

        }
        while (txArray.length() > 0);

        final int txFound = totalTxFound;
        final int txCount = totalTxCount;

        SwingUtilities.invokeLater(() ->
        {
            marketPanel.progressBar.setValue(100);
            marketPanel.progressBar.setString("Found " + Utilities.numberFormat(txFound) + " transactions  |  "
                    + "Total transactions : " + Utilities.numberFormat(txCount) + "  |  "
                    + "Total lookup time : " + Utilities.MillisToDayHrMinSec(System.currentTimeMillis() - startTime));
        });

        //Don't fill table in EDT, connection will be closed by then
        dbManager.FillJTableOrder("all_trades", "timestamp", "desc", marketPanel.txTable, connection);
//        System.err.println("transactions in table : " + marketPanel.txTable.getRowCount());   

    }  
     
     private static int[] extractTrades(String foreignBlockchain,MarketPanel marketPanel, Connection connection) 
             throws ConnectException, TimeoutException, IOException,SQLException
     {        
         DatabaseManager dbManager = marketPanel.dbManager;
        JTable tradesJTable = marketPanel.ltcTradesTable;

        String tradesDbTable = "ltc_trades";
        String totalForeign = "total_ltc";
        String qortPerForeign = "qort_per_ltc";
        String foreignPerQort = "ltc_per_qort";
        String usdForeignTable = "usd_ltc";
        String usdPerForeign = "usd_per_ltc";
        String foreignPerUsd = "ltc_per_usd";
        String usdPair = "USDC_LTC";
        String foreignQortTable = "ltc_qort";
        int averageBy = 35;

        switch (foreignBlockchain)
        {
            case "BITCOIN":
                tradesJTable = marketPanel.btcTradesTable;
                tradesDbTable = "btc_trades";
                totalForeign = "total_btc";
                qortPerForeign = "qort_per_btc";
                foreignPerQort = "btc_per_qort";
                usdForeignTable = "usd_btc";
                usdPerForeign = "usd_per_btc";
                foreignPerUsd = "btc_per_usd";
                usdPair = "USDC_BTC";
                foreignQortTable = "btc_qort";
                averageBy = 10;
                break;
            case "DOGECOIN":
                tradesJTable = marketPanel.dogeTradesTable; 
                tradesDbTable = "doge_trades";
                totalForeign = "total_doge";
                qortPerForeign = "qort_per_doge";
                foreignPerQort = "doge_per_qort";
                usdForeignTable = "usd_doge";
                usdPerForeign = "usd_per_doge";
                foreignPerUsd = "doge_per_usd";
                usdPair = "USDC_DOGE";
                foreignQortTable = "doge_qort";
                averageBy = 10;
                break;
        }
                
        if(!dbManager.TableExists(tradesDbTable, connection))
            dbManager.CreateTable(new String[]{tradesDbTable,"timestamp","long","blockheight","int","amount","double",
                totalForeign,"double",foreignPerQort,"double",qortPerForeign,"double","signature","varchar(100)"}, connection);       

        Object lastEntry = dbManager.GetFirstItem(tradesDbTable, "timestamp","timestamp","desc", connection);     

        //Find starting point for poloniex lookups            
        long firstTransactionTimestamp = (long)dbManager.GetFirstItem("all_trades", "timestamp","timestamp", "asc", connection);

        Statement statement = connection.createStatement();
        ResultSet resultSet;

        // <editor-fold defaultstate="collapsed" desc="Get all FOREIGN/QORT trades">         
         if(lastEntry == null)
            resultSet = statement.executeQuery("select * from all_trades where foreign_chain = " 
                    + Utilities.SingleQuotedString(foreignBlockchain));
        else
            resultSet = statement.executeQuery("select * from all_trades where foreign_chain = " 
                    + Utilities.SingleQuotedString(foreignBlockchain) + " and timestamp > " + String.valueOf(lastEntry));               

        marketPanel.progressBar.setValue(0);
         marketPanel.progressBar.setStringPainted(true);                   

        JSONObject atAddressObject;
        String jsonString;
        int tradesCount = 0;

        while(resultSet.next())
        {
            jsonString = resultSet.getString("at_json");
            atAddressObject = new JSONObject(jsonString);

            //gets the timestamp of the purchase (not deploy timestamp)
            long timestamp = resultSet.getLong("timestamp");
            int blockHeight = resultSet.getInt("blockHeight");
            double amount = resultSet.getDouble("amount");
            String signature = resultSet.getString("signature");
            double totalPaidForeign = atAddressObject.getDouble("expectedForeignAmount");
            double foreignPerQortPrice = Utilities.divide(totalPaidForeign, amount);
            double qortPerForeignPrice = Utilities.divide(1, foreignPerQortPrice);

            dbManager.InsertIntoDB(new String[]{tradesDbTable,
                "timestamp", String.valueOf(timestamp),
                "blockheight", String.valueOf(blockHeight),
                "amount", String.valueOf(amount),
                totalForeign, String.valueOf(totalPaidForeign),
                qortPerForeign, String.valueOf(qortPerForeignPrice),
                foreignPerQort, String.valueOf(foreignPerQortPrice),
                "signature",Utilities.SingleQuotedString(signature)}, connection);

            tradesCount++;
        }      
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="Get all USD/FOREIGN prices from poloniex between first tx and current time">
        if(!dbManager.TableExists(usdForeignTable, connection))
            dbManager.CreateTable(new String[]{usdForeignTable,"timestamp","long",usdPerForeign,"double",foreignPerUsd,"double"}, connection);

        //It's possible that the last usd/ltc entry is later than the lates ltc/qort trade
//                Object lastEntryTrade = dbManager.GetFirstItem(tradesDbTable, 
//                        "timestamp","where timestamp > " + lastEntry,"timestamp", "desc", connection);


        Object lastUsdTimestamp = dbManager.GetFirstItem(usdForeignTable, 
                "timestamp","timestamp", "desc", connection);

        //if no usd entry was found we collect poloniex data from the first known transaction until the last known trade of this pair
        long startTimestamp = lastUsdTimestamp == null ? firstTransactionTimestamp : (long) lastUsdTimestamp; 
        long endTimestamp =  System.currentTimeMillis();  //( long) dbManager.GetFirstItem(tradesDbTable, "timestamp", "timestamp", "desc", connection); 
        startTimestamp /= 1000;//timestamp to seconds
        endTimestamp /= 1000;//timestamp to seconds

        jsonString = Utilities.ReadStringFromURL(
                "https://poloniex.com/public?command=returnChartData&currencyPair=" + usdPair + "&start="
                + startTimestamp+ "&end=" + endTimestamp + "&resolution=auto");
                     
        
        boolean usdLookupSuccess = true;
        if(jsonString == null)
        {
            usdLookupSuccess = false;
            marketPanel.lookupStatusLabel.setText(Utilities.AllignCenterHTML(
                    "Failed to look up US Dollar prices from poloniex.com<br/>"
                + "Either the poloniex service or your internet connection is down<br/>"
                + "US dollar prices may not be up to date"));
        }
        else
        {
            JSONArray pricesArray = new JSONArray(jsonString);
            JSONObject jSONObject;

            for(int i = 0; i < pricesArray.length(); i++)
            {
                jSONObject = pricesArray.getJSONObject(i);

                double weightedAverage = jSONObject.getDouble("weightedAverage");

                if(weightedAverage == 0)//poloniex returns invalid data (date = 0)
                    continue;

                dbManager.InsertIntoDB(new String[]{usdForeignTable,
                    "timestamp", String.valueOf(jSONObject.getLong("date") * 1000),//UNIX time is in seconds
                    usdPerForeign, String.valueOf(weightedAverage),
                    foreignPerUsd,String.valueOf(Utilities.divide(1 , weightedAverage) )}, connection);       
            } 
        }  
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="Create weighted average FOREIGN/QORT prices table ">
       //using a weighted average (100) to account for spikes caused by low volume high/low priced trades                 
        if(!dbManager.TableExists(foreignQortTable, connection))
            dbManager.CreateTable(new String[]{foreignQortTable,"timestamp","long",foreignPerQort,"double",
                qortPerForeign,"double","amount","double"}, connection);        

        //Opted to delete and re-populate these tables on every update, instead of looking up the 
        //last entry and updating from where we left off last time
        dbManager.ExecuteUpdate("delete from " + foreignQortTable, connection);                

        var lastTradesBatch = new ArrayList<Double>();//amount * price
        var lastAmountsBatch = new ArrayList<Double>();//amount
        statement = connection.createStatement();
        resultSet = statement.executeQuery(
                "select timestamp,amount," + foreignPerQort + " from " + tradesDbTable + " order by timestamp asc");

        int count = 0;
        
        while(resultSet.next())
        {    
            count++;
            
            double amount = resultSet.getDouble("amount");
            double foreignPerQortPrice = resultSet.getDouble(foreignPerQort);

            lastTradesBatch.add(Utilities.multiply(amount, foreignPerQortPrice));
            lastAmountsBatch.add(amount);

            if(lastTradesBatch.size() < averageBy)
                continue;
            
            double totalTradesVolume = 0;//amount * price
            double totalTradesAmount = 0;//amount

            for(int i = 0; i < averageBy; i++) 
            {
                //add total amount * price for last ten trades
                totalTradesVolume = Utilities.add(totalTradesVolume, lastTradesBatch.get(i));
                //add all amounts for last ten trades
                totalTradesAmount = Utilities.add(totalTradesAmount,lastAmountsBatch.get(i));
            }

            //divide total (amount * price) by total amount to get weighted average
            double weightedAverage = Utilities.divide(totalTradesVolume, totalTradesAmount);

            //remove trade 0 from batches, next iteration will add trade 0 + averageBy to the end of the lists
            lastTradesBatch.remove(0);
            lastAmountsBatch.remove(0);

            long timestamp = resultSet.getLong("timestamp");

            //we use the amount of the current timestamp as the volume (no need to average that)
            dbManager.InsertIntoDB(new String[]{foreignQortTable,
                "timestamp", String.valueOf(timestamp),
                foreignPerQort, String.valueOf(weightedAverage),
                qortPerForeign,String.valueOf(Utilities.divide(1, weightedAverage)),
                "amount",String.valueOf(amount)}, connection);                
        }
        // </editor-fold>                  

        //Create QORT/USD price table using weighted LTC or BTC (first 77 trades) price        
        if(usdLookupSuccess && (foreignBlockchain.equals("BITCOIN") || foreignBlockchain.equals("LITECOIN")))
            extractUsdPrices(foreignBlockchain, foreignQortTable, foreignPerQort, usdForeignTable, usdPerForeign, connection,dbManager,marketPanel);

        dbManager.FillJTable(tradesDbTable, "timestamp", tradesJTable, connection);
         marketPanel.progressBar.setValue(100);
         marketPanel.progressBar.setString("Found " + tradesJTable.getRowCount() + " " + foreignBlockchain + " trades");    
         
         return new int[]{tradesCount,tradesJTable.getRowCount()};
     }
     
     //If we want more granular/accurate data for the usd price, we'll need to query the poloniex API in smaller chunks. 
     //For the more recent data, the prices will probably be updated in 5 minute intervals, but for older data (due to the long
     //period) the resolution will be 2 hours (or more)
     private static  void extractUsdPrices(String foreignBlockchain,String foreignQortTable,String foreignPerQort,String usdForeignTable,
                                                    String usdPerForeign, Connection connection,DatabaseManager dbManager,MarketPanel marketPanel) throws SQLException
     {
           if(!dbManager.TableExists("usd_qort", connection))
                    dbManager.CreateTable(new String[]{"usd_qort","timestamp","long","usd_per_qort","double","qort_per_usd","double"}, connection);
                 
                 //We first find all btc to usd prices. From genesis untill implementationof LTC  (january 2021) we need to use btc to usd prices
                 //to find qort to usd price. Btc iteration stops after 77 trades (hardcoded) from then on we use LTC
                //only clear table on first iteration, which is the BTC lookup
                 if(foreignBlockchain.equals("BITCOIN"))                
                    dbManager.ExecuteUpdate("delete from usd_qort", connection);//clear usd_qort table on bitcoin iteration (1st)
                 
                 //get all foreign/qort timestamps and prices 
                 Statement statement = connection.createStatement();                 
                 ResultSet foreignQortResultSet = statement.executeQuery(
                         "select timestamp," + foreignPerQort + " from " + foreignQortTable + " order by timestamp asc");       
                 //get all usd/foreign timestamps and prices (from the poloniex results, will be many more than the ltc/qort which are from trade portal)
                 //(scroll insensitive to enable multiple iterations of same resultset)
                 statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                 ResultSet usdForeignResultSet = statement.executeQuery(
                         "select timestamp," + usdPerForeign + " from " + usdForeignTable + " order by timestamp asc");
                 
                int lastIndex = 0;
                int count = 0;
                
                final int rowCount = dbManager.getRowCount(foreignQortTable, connection);
                
                //iterates over every ltc/qort entry timestamp and compares to foreign/usd timestamp. The closest (and larger than) timestamp
                //will be used to calculate the usd/qort price
                while(foreignQortResultSet.next())
                {                             
                    long timestamp = foreignQortResultSet.getLong("timestamp");
                    
//                    System.err.println(Utilities.DateFormat(timestamp));
                    
                    //skip to index of last found timestamp
                    usdForeignResultSet.absolute(lastIndex);
                    
                    while(usdForeignResultSet.next())
                    {
                         lastIndex = count;
                        long timestamp2 = usdForeignResultSet.getLong("timestamp");

                        if (timestamp2 > timestamp)
                        {     
                            //the poloniex data over long periods will return a resolution of 2 hours, trades in the portal could be
                            //at any interval. If the trades' timestamp is 2 hours within current usd/foreign timestamp, decrement
                            //count here, the increment after the break ensures the same value will be checked for the next trade
                             if(timestamp2 - timestamp > 7200000)
                                 count--;
                             
                            double usdPerForeignPrice = usdForeignResultSet.getDouble(usdPerForeign);
                            double foreignPerQortPrice = foreignQortResultSet.getDouble(foreignPerQort);
                            double usdPerQortPrice = Utilities.multiply(foreignPerQortPrice, usdPerForeignPrice);
                            double qortPerUsdPrice = Utilities.divide(1, usdPerQortPrice);                             
                            
//                            System.err.println(Utilities.DateFormat(timestamp2) + "\n");
                            
                            dbManager.InsertIntoDB(new String[]{"usd_qort",
                                "timestamp", String.valueOf(timestamp),
                                "usd_per_qort", String.valueOf(usdPerQortPrice),
                                "qort_per_usd",String.valueOf(qortPerUsdPrice)}, connection);
                            
                            break;
                        }
                    }   
                    count++;                       
                      
                    //This is the loop that needs to update the progress bar as it is the most expensive/time consuming one
                    final int current = count;

                    SwingUtilities.invokeLater(() ->
                    {
                        double percent = ((double) current / rowCount) * 100; //    (highest - (lastBlockHeight - lastCheckedBlock)) / highest) * 100;
                         marketPanel.progressBar.setValue((int) percent);
                         marketPanel.progressBar.setString(String.format("%.2f%% done  |  %s " + foreignBlockchain + " trades found", percent, Utilities.numberFormat(current)));                         
                    });                      
                }   
        }        
    
}
