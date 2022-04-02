package marketwatch;

import enums.Extensions;
import io.FileWrapperStyle;
import io.FileWrapperLayout;
import io.FileWrapperArrange;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;
import javax.swing.JOptionPane;
import org.h2.store.fs.FilePath;
import org.h2.store.fs.FilePathWrapper;

public class ConnectionDB 
{        
    //Using a generic FileWrapper and passing the extension to it was not working. The only way the wrapper
    //seems to work is when hardcoding the extension into the MAPPING array on initialisation or in a constructor
    //without any arguments. This means that we need to create a separate wrapper class for every file type. 
    private static ArrayList<FilePathWrapper> wrappers = new ArrayList<>()
    {
        {
            //adding the wrappers with a switch statement inside the Extensions.values() iterator
            //ensures that the index of the wrapper in the list is the same as the index of the extension
            //in Extensions. This is important when we're registering the filepath, we need to get the right 
            //wrapper for the extension that is passed as an argument to the connection
            for(Extensions value : Extensions.values())
            {
                switch(value)
                {
                    case LAYOUT:
                        add(new FileWrapperLayout());
                        break;
                    case STYLE:
                        add(new FileWrapperStyle());
                        break;
                    case ARRANGE:
                        add(new FileWrapperArrange());
                        break;
                }
            }
        }
    };
    
    /** * This version of H2 automatically creates a database if it doesn't exist.<br>
 IF_EXISTS argument provided by H2 was causing connection errors.<br>
 Create all databases with this method, only connect to databases that are known to exist.
     * @param database
     * @param username
     * @param password
     * @param folder*/
    public static void CreateDatabase(String database,String username,char[] password,String folder)
    {   
        folder = "jdbc:h2:./" + folder + "/";
        
        try 
        {            
            Class.forName("org.h2.Driver");              
            char[] passwords = (String.copyValueOf(password) + " " + String.copyValueOf(password)).toCharArray();
            String url = folder + database + ";CIPHER=AES"
                    + ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";        
        
            Properties prop = new Properties();
            prop.setProperty("user", username);
            prop.put("password", passwords);
            Connection c = DriverManager.getConnection(url, prop);
            c.close();        
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
    }  
    
    public static Connection getConnection(String database,String username, char[] password, String folder) throws NullPointerException
    {    
        folder = "jdbc:h2:./" + folder + "/";
        
    //<editor-fold defaultstate="collapsed" desc="we can't use the parameter password">    
        //if we want to implement clearing the char[] (done automatically by getUnencryptedConnection(url,prop) )
        // we need to store (persist) it or encrypt it and decrypt when needed, so we can pass a new char[] every
        //time, if we clear the original char[], then the next time we call this method the password will be invalid
        //we can't use the password variable used by the program as it will be cleared by getUnencryptedConnection 
        //leaving us without a password in memory 
         //</editor-fold>
        char[] passwords = (String.copyValueOf(password) + " "  + String.copyValueOf(password)).toCharArray();         
        
         String url = folder +  database + ";CIPHER=AES" +
                    ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";           
         
        Properties prop = new Properties();
        prop.setProperty("user", username);
        prop.put("password", passwords);        
        try 
        {    
            Class.forName("org.h2.Driver");
            return DriverManager.getConnection(url,prop);
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], database)));
            throw new NullPointerException();
        }
    }      
    
     public static void CreateDatabase(String database,String folder)
    {  
        folder = "jdbc:h2:./" + folder + "/";
        
        try 
        {            
            Class.forName("org.h2.Driver");              
            String url = folder + database + ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";        
        
            Connection c = DriverManager.getConnection(url);
            c.close();        
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
    }  
     
    public static Connection getConnection(String database,String folder)
    {   
        folder = "jdbc:h2:./" + folder + "/";
        
        try 
        {            
            Class.forName("org.h2.Driver");              
            String url = folder + database + ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";        
        
            return DriverManager.getConnection(url);
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], database)));
            throw new NullPointerException();
        }
    }    
    
    public static boolean CanConnect(String database, String username, char[] password, String folder)
    {   
        folder = "jdbc:h2:./" + folder + "/";
        
        char[] passwords = (String.copyValueOf(password) + " "  + String.copyValueOf(password)).toCharArray();    
        String url = folder + database +
                ";CIPHER=AES;TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";

        Properties prop = new Properties();
        prop.setProperty("user", username);
        prop.put("password", passwords);
        try
        {
            Class.forName("org.h2.Driver");
            Connection c = DriverManager.getConnection(url, prop);
            c.close();
            return true;
        }
        catch (ClassNotFoundException | SQLException e)
        {
            return false;
        }
    }
    
    public static void CreateDatabase(String database,String folder, Extensions extension) //,String folder)
    {      
        int wrapperIndex = Extensions.valueOf(extension.toString()).ordinal();
        FilePath.register(wrappers.get(wrapperIndex));        
        
        try 
        {            
            Class.forName("org.h2.Driver");              
            String url =  "jdbc:h2:" + extension.get()+ ":./" + folder + "/" +  database + 
                    ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";        
        
            Connection c = DriverManager.getConnection(url);
            c.close();        
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
    }   
    
    public static Connection getConnection(String database,String folder, Extensions extension)
    {   
        //Extension toString() gives the type as string, Extension.get() gives the actual extension
        int wrapperIndex = Extensions.valueOf(extension.toString()).ordinal();
        FilePath.register(wrappers.get(wrapperIndex));   

        try 
        {            
            Class.forName("org.h2.Driver");              
            String url =  "jdbc:h2:" + extension.get()+ ":./" + folder + "/" +  database + 
                    ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0";       
            
            return DriverManager.getConnection(url);
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], database)));
            throw new NullPointerException();
        }
    }   
    
}
