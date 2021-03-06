package us.ihmc.etherCAT.master;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.locks.LockSupport;

import us.ihmc.etherCAT.master.EtherCATStatusCallback.TRACE_EVENT;

/**
 * Description of the SyncManager. Holds PDO information 
 * 
 * @author Jesper Smith
 *
 */
public class SyncManager
{
   public enum MailbusDirection 
   {
      TXPDO,
      RXPDO,
      UNKNOWN
   };
   
   private final ArrayList<PDO> PDOs = new ArrayList<>();
   private final boolean cconfigurePDOs;
   private final int index;
   
   private boolean configured = false;
   
   private MailbusDirection direction = MailbusDirection.UNKNOWN;
   
   /**
    * Create new Sync Manager at index 0-3. 
    * 
    * @param index Index of the sync manager, 0 - 3.
    * @param configurePDOs If true, the PDO's will be configured on the slave to match the desired configuration.
    */
   public SyncManager(int index, boolean configurePDOs)
   {
      this.index = index;
      this.cconfigurePDOs = configurePDOs;
   }
   
   /**
    * Internal function that sends SDO requests to configure PDOs.
    * 
    * @param slave
    */
   void configure(Master master, Slave slave)
   {
      configured = true;
      
      if(cconfigurePDOs)
      {
         int pdoConfigurationIndex = 0x1C10 + index;
         master.getEtherCATStatusCallback().trace(this, slave, TRACE_EVENT.CLEAR_PDOS);
         
         LockSupport.parkNanos(10000000);
         // Set the size of the SM configuration array to zero, allows writing to the elements
         int wc = slave.writeSDO(pdoConfigurationIndex, 0x0, (byte) 0);
         
         if(wc == 0)
         {
            master.getEtherCATStatusCallback().pdoConfigurationError(slave, index, pdoConfigurationIndex);

         }
         
         master.getEtherCATStatusCallback().trace(this, slave, TRACE_EVENT.WRITE_PDOS);
         // Configure all the PDOs
         for(int i = 0; i < PDOs.size(); i++)
         {

            LockSupport.parkNanos(10000000);
            wc = slave.writeSDO(pdoConfigurationIndex, i + 1, (short) PDOs.get(i).getAddress());
            if(wc == 0)
            {
               master.getEtherCATStatusCallback().pdoConfigurationError(slave, index, pdoConfigurationIndex, PDOs.get(i).getAddress(), i + 1);
            }
         }
         
         master.getEtherCATStatusCallback().trace(this, slave, TRACE_EVENT.WRITE_PDO_SIZE);
         // Set the correct size of the SM array

         LockSupport.parkNanos(10000000);
         wc = slave.writeSDO(pdoConfigurationIndex, 0x0, (byte) PDOs.size());
         if(wc == 0)
         {
            master.getEtherCATStatusCallback().pdoConfigurationError(slave, index, pdoConfigurationIndex);
         }
      }
   }

   /**
    * 
    * @return Index of this syncmanager, 0 - 3
    */
   public int getIndex()
   {
      return index;
   }

   /**
    * Register a new PDO.
    * 
    * SyncManagers can only register RxPDO's or TxPDO's. If a TxPDO has previously been registered, this function will fail.
    * 
    * @param pdo
    */
   public void registerPDO(RxPDO pdo)
   {
      if(configured)
      {
         throw new RuntimeException("Cannot register PDOs after initializing the master");
      }
      switch(direction)
      {
      case TXPDO:
         throw new RuntimeException("Cannot register both Tx and Rx PDO entries on a single syncmanager");
      case UNKNOWN:
         direction = MailbusDirection.RXPDO;
      case RXPDO:
         PDOs.add(pdo);
         break;
      }
   }
   

   /**
    * Register a new PDO.
    * 
    * SyncManagers can only register RxPDO's or TxPDO's. If a RxPDO has previously been registered, this function will fail.
    * 
    * @param pdo
    */
   public void registerPDO(TxPDO pdo)
   {
      if(configured)
      {
         throw new RuntimeException("Cannot register PDOs after initializing the master");
      }
      switch(direction)
      {
      case RXPDO:
         throw new RuntimeException("Cannot register both Tx and Rx PDO entries on a single syncmanager");
      case UNKNOWN:
         direction = MailbusDirection.TXPDO;
      case TXPDO:
         PDOs.add(pdo);
         break;
      }
   }
   
   /**
    * 
    * @return The mailbus direction (TXPDO or RXPDO) based on PDO's registered
    */
   public MailbusDirection getMailbusDirection()
   {
      return direction;
   }

   /**
    * @return size of all PDOs in this sync manager
    */
   public int processDataSize()
   {
      int size = 0;
      for(int i = 0; i < PDOs.size(); i++)
      {
         size += PDOs.get(i).size();
      }
      return size;
   }

   
   /**
    * Internal function. Maps the PDOs to the underlying data storage.
    * 
    * @param ioMap
    * @param inputOffset
    * @throws IOException 
    * 
    */
   void linkBuffers(ByteBuffer ioMap, BufferOffsetHolder inputOffset) throws IOException
   {
      for(int i = 0; i < PDOs.size(); i++)
      {
         try
         {
            PDOs.get(i).linkBuffer(ioMap, inputOffset);
         }
         catch (IOException e)
         {
            throw new IOException("SM[" + getIndex() + "]: " + e.getMessage());
            
         }
      }
      
   }

}
