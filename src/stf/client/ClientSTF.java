package serveurstf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 *
 * @author Ophélie EOUZAN
 * @author Mélanie DUBREUIL
 * 
 */
public class ClientSTF
{
    // TFTP OP CODE    
    private static final byte OP_ZERO = 0;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    
    // ERROR CODES
    public static final int ERR_SOCKET = -1;
    public static final int ERR_PACKET = -2;
    public static final int ERR_TIMEOUT = -3;
    public static final int ERR_FILEUNKNOWN = -4;
    public static final int ERR_HOST = -5;
    
    public final int TEMPO = 10;
    public final int BUFFER = 512; // 512 + 4

    private int code = 0;
    private String errorMsg = "";
    private DatagramSocket ds;
    private InetAddress inetServerAddress;
    private int serverPort;
    public String mode = "octet";
    private Exception exception = null;

    /**
     * Constructor.
     * @param ip
     * @param port 
     */
    public ClientSTF(String ip, int port)
    {
        try {
            serverPort = port;
            inetServerAddress = InetAddress.getByName(ip);

            ds = new DatagramSocket();
            // ds.setSoTimeout(TEMPO);
        } catch (IOException ex) {
            if (ex instanceof SocketException) {
                code = ERR_SOCKET;
            } else if (ex instanceof UnknownHostException) {
                code = ERR_HOST;
            } else {
                code = ERR_PACKET;
            }
            errorMsg = "Return code : " + code + " [ " + ex.getClass() + " - " + ex.getMessage() + " ] ";
            System.err.println(errorMsg);
        }
    }
    
    /**
     * Receive a file from a TFTP server.
     * @param localFileName
     * @param remoteFileName
     * @return int code
     */
    public int receiveFile(String localFileName, String remoteFileName)
    {
        // Initialization
        this.flush();
        byte firstByte = 0;
        int ackNumber = 0, dataSize;
        byte[] data = new byte[BUFFER + 4];
        File localFile = null;
        FileOutputStream fos;
        DatagramPacket recieveDatagram;

        try {
            // Create local file
            localFile = new File(localFileName);
            fos = new FileOutputStream(localFile);

            // Send Read request
            this.sendWriteOrSendRequest(remoteFileName, OP_RRQ);

            do {
                // Recieve data
                System.out.println("Waiting packet " + (ackNumber + 1));
                recieveDatagram = new DatagramPacket(data, data.length);
                ds.receive(recieveDatagram);
                data = recieveDatagram.getData();
                dataSize = recieveDatagram.getLength();

                // Get block packetNumber OR code error
                int packetNumber = ((firstByte & 0xff) << 8)|(data[3] & 0xff);
                if (255 == (data[3] & 0xff)) {
                    firstByte++; // Incrementation of first byte when second byte equals 255, because it wasn't done automatically.
                }
                System.out.print("Received packet " + packetNumber + " : ");

                // Read op code
                if (data[1] == OP_DATA) {
                    System.out.println("DATA size " + dataSize + " octets");
                    if (packetNumber == ackNumber + 1) {
                        // Write data in file
                        fos.write(data, 4, dataSize - 4);
                        System.out.println("Write DATA " + packetNumber);
                    }

                    // Create ACK request
                    ackNumber = packetNumber;
                    System.out.println("Sending ACK " + ackNumber);
                    byte[] ack = { 0, OP_ACK, (byte)(ackNumber / 256), (byte)(ackNumber % 256) };

                    // Send ack datagram
                    DatagramPacket ackDatagram = new DatagramPacket(ack, 4, inetServerAddress, recieveDatagram.getPort());
                    ds.send(ackDatagram);
                    System.out.println("Sended ACK ");
                } else if (data[1] == OP_ERROR) {
                    this.handleServerError(packetNumber, data);
                    localFile.delete();
                    break;
                }
            } while (dataSize == (BUFFER + 4));
            
            // Close local file
            fos.close();
        } catch (IOException ex) {
            // Delete local corrupted file
            if(localFile != null) {
                localFile.delete();
            }

            // Handle exception
            if(ex instanceof InterruptedIOException) {
                code = ERR_TIMEOUT;
            } else if(ex instanceof SocketException) {
                code = ERR_SOCKET;
            } else if (ex instanceof FileNotFoundException) {
                code = ERR_FILEUNKNOWN;
            } else {
                code = ERR_PACKET;
            }
            exception = ex;
        } finally {
            return this.close();
        }
    }

    /**
     * Send a file to a TFTP server.
     * @param localFileName
     * @return int code
     */
    public int sendFile(String localFileName)
    {  
        // Initialisation
        this.flush();
        int blockNumber = 1, dataBlockSize = 0;
        byte[] buffer = new byte[BUFFER];
        FileInputStream fileReader;
        DatagramPacket receivedAckPacket;

        try {
            // Open local file
            fileReader = new FileInputStream(localFileName);
            
            // Send Write request
            this.sendWriteOrSendRequest(localFileName, OP_WRQ);
            
            do {
                // Receive Ack
                System.out.println("Waiting ACK " + blockNumber);
                byte[] dataReceived = new byte[BUFFER];
                receivedAckPacket = new DatagramPacket(dataReceived, BUFFER);
                ds.receive(receivedAckPacket);

                // Get block packetNumber OR code error
                int packetNumber = ((dataReceived[2] & 0xff) << 8)|(dataReceived[3] & 0xff);
                if (dataReceived[1] == OP_ACK) {
                    System.out.println("ACK  : " + packetNumber);
                    if (packetNumber == blockNumber - 1) {
                        // Read data to send
                        dataBlockSize = fileReader.read(buffer, 0, BUFFER);
                    }
                    System.out.println("Size of Data to send : " + dataBlockSize);
                    
                    // Send data
                    byte[] data;
                    if (dataBlockSize != -1) {
                        System.out.println("Send data : " + blockNumber);
                        data = this.createDataPacket(buffer, dataBlockSize, blockNumber);
                    } else {
                        // In case of last the last datas send is of size 512, causing the server to wait event htough the file was entirely send.
                        // It happened for us with a file of 8,50 Ko, the last packet send was of size 512 (+4 = 516), and so the server was waiting but we had no dataReceived to send anymore.
                        System.out.println("Send last packet");
                        data = this.createDataPacket(new byte[0], 0, blockNumber);
                    }
                    ds.send(new DatagramPacket(data, data.length, inetServerAddress, receivedAckPacket.getPort()));
                    blockNumber++;
                } else if (dataReceived[1] == OP_ERROR) {
                    this.handleServerError(packetNumber, dataReceived);
                    break;
                }
            } while(dataBlockSize != -1); // Tant qu'il y a des données à envoyer
            
            // Close local file
            fileReader.close();
        } catch (IOException ex) {
            if(ex instanceof FileNotFoundException) {
                code = ERR_FILEUNKNOWN;
            }
            exception = ex;
        } finally {
            return this.close();
        }
    }
    
    public int getCode()
    {
        return code;
    }
    
    public String getErrorMsg()
    {
        return errorMsg;
    }
    
    /**
     * Initalize all recurrent variables.
     */
    private void flush()
    {
        code = 0;
        errorMsg = "";
        exception = null;
    }
    
    /**
     * Handle server errors by traducing the code and getting the error message.
     * @param errorCode Block number of the error packet
     * @param data      Error packet content
     */
    private void handleServerError(int errorCode, byte[] data)
    {
        System.out.println("ERROR");
        code = errorCode + 1;
        errorMsg = new String(data, 4, data.length - 5);
    }

    /**
     * Closes the DatagramSocket and handles the returned response.
     * @return int code
     */
    public int close()
    {
        ds.close();
        if (exception != null) {
            errorMsg = exception.getClass() + " - " + exception.getMessage();
        }
        if (!errorMsg.isEmpty()) {
            System.err.println("Return code : " + code + " - " + errorMsg);
        } else {
            System.out.println("Return code : " + code);
        }

        return code;
    }
    
    private void sendWriteOrSendRequest(String remoteFileName, byte opCode) throws IOException
    {
        // Packet : 2 OP_CODE bytes - n FILE NAME bytes - 1 ZERO byte - n FILE LENGTH bytes for - 1 ZERO byte
        int requestLength = 2 + remoteFileName.length() + 1 + mode.length() + 1;
        byte[] request = new byte[requestLength];
        
        // Request content
        int i = 0;
        request[i] = OP_ZERO;
        request[++i] = opCode;
        for (int y = 0; y < remoteFileName.length(); y++) {
            request[++i] = (byte) remoteFileName.charAt(y);
        }
        request[++i] = OP_ZERO;
        for (int y = 0; y < mode.length(); y++) {
            request[++i] = (byte) mode.charAt(y);
        }
        request[++i] = OP_ZERO;

        // Sending request to the server
        ds.send(new DatagramPacket(request, request.length, inetServerAddress, serverPort));
    }
    
    private byte[] createDataPacket(byte[] byteRead, int length, int blockNumber)
    {
        // Packet creation
        byte[] data = new byte[4 + length];
        data[0] = OP_ZERO;
        data[1] = OP_DATA;
        data[2] = (byte)(blockNumber/256);
        data[3] = (byte)(blockNumber%256);
        
        // Fill with dataReceived content
        for(int i = 0 ; i < length; i++) {
            if (i >= data.length) {
                break;
            }
            data[i + 4] = byteRead[i];
        }

        return data;
    }
}
