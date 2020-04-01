import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class ClientDNS {

    private static String dnsServer = "8.8.8.8";
    private static int port = 53;
    public static InetAddress ipAddress;
    int identifier;

    ClientDNS(){
    }

    //setare nume server si port
    ClientDNS(String dnsServer, int port){
        this.dnsServer = dnsServer;
        this.port = port;
    }

    byte[] getRequestByteArray(String domain){
        byte[] requestByteArray = new byte[12 + domain.length() + 6]; //array de octeti necesar cererii

        // Identifier
        identifier = 0; // intreg pe doi octeti; ales 0 pentru simplitate

        byte[] identifierBytes = ByteBuffer.allocate(4).putInt(identifier).array();
        byte MSB = identifierBytes[2];
        byte LSB = identifierBytes[3];

        requestByteArray[0] = MSB;
        requestByteArray[1] = LSB;
        requestByteArray[2] = 0x01;         // Recursion Desired
        requestByteArray[5] = 0x01;        // Question Count (1 intrebare)

        int questionIndex = 12;        // de la octetul 12 incepe Question Name
        byte questionBuffer[] = new byte[domain.length()];
        int k = 0;
        char[] domainNameChars = domain.toCharArray();

        for (int j = 0; j < domain.length(); ++j) // socare domeniu
        {
            if (domainNameChars[j] != '.') {
                questionBuffer[k++] = (byte) domainNameChars[j];
            } else {
                requestByteArray[questionIndex++] = (byte) k;
                for (int i = 0; i < k; ++i) {
                    requestByteArray[questionIndex++] = questionBuffer[i];
                }
                k = 0;
            }

            if (j == domain.length() - 1) {
                requestByteArray[questionIndex++] = (byte) k;
                for (int i = 0; i < k; ++i) {
                    requestByteArray[questionIndex++] = questionBuffer[i];
                }
            }
        }
        requestByteArray[questionIndex] = 0x0;         //terminatorul de nume
        requestByteArray[questionIndex + 2] = 0x1;     // Question Type -> 1 = adresa IP
        requestByteArray[questionIndex + 4] = 0x1;     // Question Class -> 1 = clasa internet
        //header cerere creat
        System.out.println("Pachet pentru domeniul \"" + domain + "\":");
        //printData(requestByteArray);
        //System.out.println();

        return requestByteArray;
    }

    //rezolva cerere pentru domeniul DNS specificat la constructie
    String solveDomain(String domain) throws IOException
    {
        byte[] requestByteArray = getRequestByteArray(domain);

        // construim un datagram cu destinatarul IP-ul serverului DNS, portul 53
        DatagramSocket datagramSocket = new DatagramSocket();
        try {
            datagramSocket.setSoTimeout(2000);
            ipAddress = InetAddress.getByName(dnsServer);
            DatagramPacket requestPacket = new DatagramPacket(requestByteArray, requestByteArray.length, ipAddress, port);

            //trimitere pachet
            datagramSocket.send(requestPacket);
            System.out.println("Pachetul trimis catre " + dnsServer + ".");

            // preluam raspunsul de la server intr-un buffer de 512 octeti
            byte[] responseByteBuffer = new byte[512];

            DatagramPacket responsePacket = new DatagramPacket(responseByteBuffer, 512);
            datagramSocket.receive(responsePacket);
            System.out.println("Pachetul primit de la server");

           // printData(responseByteBuffer);
            datagramSocket.close();

            // Analizare rasuns primit
            // verificam intai identificatorul sa se potriveasca cu cel al cererii
            byte LSB = responseByteBuffer[1];
            byte MSB = responseByteBuffer[0];
            int receivedIdentifier = (((0xFF) & MSB) << 8) | (0xFF & LSB);

            if (identifier == receivedIdentifier) {
                System.out.println("Identificatorii se potrivesc");
            }else{
                System.out.println("Identificatorii nu se potrivesc");
                return null;
            }

            // verificam validitatea raspunsului -> sa nu se fi produs vreo eroare
            //al patrulea octet, RCode, sa fie 0
            if ((responseByteBuffer[3] & 0x0F) == 0x00)
            {
                //System.out.println("RCode = 0");
            } else {
                int errorCode = responseByteBuffer[3] & 0x0F;
               // System.out.println("Eroare: RCode = " + errorCode);
            }
            System.out.println();

            //Answer Record Count
            LSB = responseByteBuffer[7];
            MSB = responseByteBuffer[6];
            int numberOfResponses = (((0xFF) & MSB) << 8) | (0xFF & LSB);
           // System.out.println("Answer Record Count: " + numberOfResponses);

            //Name Server Count
            LSB = responseByteBuffer[9];
            MSB = responseByteBuffer[8];
            int numberOfAuthorityInfos = (((0xFF) & MSB) << 8) | (0xFF & LSB);
           // System.out.println("Name Server Count: " + numberOfAuthorityInfos);

            //Additional Record Count
            LSB = responseByteBuffer[11];
            MSB = responseByteBuffer[10];
            int numberOfAdditionalRecords = (((0xFF) & MSB) << 8) | (0xFF & LSB);
           // System.out.println("Aditional Record Count: " + numberOfAdditionalRecords);

            int answerIndex = 12 + domain.length() + 6; // pentru ca server-ul mentine informatiile din cererea clientului
           // System.out.println();

            if (numberOfAdditionalRecords + numberOfAuthorityInfos + numberOfResponses == 0)
            {
                System.out.println("Nicio informatie primita de la server.");
                return null;
            }

            System.out.println("Informatiile primite de la server sunt:");
            int recordIndex = 1;
            while (recordIndex <= numberOfResponses + numberOfAdditionalRecords + numberOfAuthorityInfos) {
                // Resource Name
                String particleName = getParticlePointer(answerIndex, responseByteBuffer);

                // trebuie sa sarim peste un numar de octeti dependent de tipul de particula
                if ((responseByteBuffer[answerIndex] & 0xFF) < 192) // dimensiune de particula
                {
                    answerIndex += particleName.length() + 1;
                } else // pointer
                {
                    answerIndex += 2; // pointer-ul e pe 2 octeti
                }
                particleName = particleName.substring(0, particleName.length() - 1);
                System.out.print("" + recordIndex + " - Numele de particula: " + particleName);

                // Record Type (2 octeti)
                MSB = responseByteBuffer[answerIndex++];
                LSB = responseByteBuffer[answerIndex++];
                int recordType = (((0xFF) & MSB) << 8) | (0xFF & LSB);
                System.out.print(" | Record Type: " + recordType + " ");
                if (recordType == 1) {
                    System.out.print("adresa IPv4");
                } else if (recordType == 2) {
                    System.out.print("server de nume");
                } else if (recordType == 5) {
                    System.out.print("nume canonic");
                } else if (recordType == 28) {
                    System.out.print("adresa IPv6");
                }

                // Record Class (2 octeti)
                MSB = responseByteBuffer[answerIndex++];
                LSB = responseByteBuffer[answerIndex++];
                int recordClass = (((0xFF) & MSB) << 8) | (0xFF & LSB);
                System.out.print(" | Record Class: " + recordClass);
                if (recordClass == 1) {
                    System.out.print(" internet");
                }

                // TTL (Time To Live) - 4 octeti
                byte b3 = responseByteBuffer[answerIndex++];
                byte b2 = responseByteBuffer[answerIndex++];
                byte b1 = responseByteBuffer[answerIndex++];
                byte b0 = responseByteBuffer[answerIndex++];
//                int TTL = ((0xFF & b3) << 24) | ((0xFF & b2) << 16) | ((0xFF & b1) << 8) | (0xFF & b0);
//                long hours = TimeUnit.MILLISECONDS.toHours(TTL);
//                TTL -= TimeUnit.HOURS.toMillis(hours);
//                long minutes = TimeUnit.MILLISECONDS.toMinutes(TTL);
//                TTL -= TimeUnit.MINUTES.toMillis(minutes);
//                long seconds = TimeUnit.MILLISECONDS.toSeconds(TTL);
                //System.out.println("TTL:" + TTL);
                //System.out.print(" | Time To Live: " + hours + "h " + minutes + "m " + seconds + "s");

                // Record Data Length (2 octeti)
                MSB = responseByteBuffer[answerIndex++];
                LSB = responseByteBuffer[answerIndex++];
                int dataLength = (((0xFF) & MSB) << 8) | (0xFF & LSB);

                if (dataLength == 4 && recordType == 1) // daca Data Length = 4, si Record Type = 1, raspunsul contine adresa IPv4
                {
                    // construim adresa IPv4
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dataLength; ++i) {
                        sb.append(responseByteBuffer[answerIndex++] & 0xFF);
                        sb.append('.');
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    System.out.println(" | Adresa IPv4: " + sb.toString());
                    return sb.toString();
                } else if (dataLength == 16 && recordType == 28) // daca Data Length = 16 si Record Type = 28, raspunsul contine adresa IPv6
                {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dataLength; ++i) {
                        sb.append(responseByteBuffer[answerIndex++] & 0xFF);
                        sb.append('.');
                    }
                    sb.deleteCharAt(sb.length() - 1);

                    System.out.println(" | Adresa IPv6: " + sb.toString());
                } else if (recordType == 2) // server de nume
                {
                    // preluam numele de particula recursiv
                    String nsName = getParticlePointer(answerIndex, responseByteBuffer);
                    answerIndex += dataLength;
                    nsName = nsName.substring(0, nsName.length() - 1);
                    System.out.println(" | Server de nume: " + nsName);
                } else if (recordType == 5) // nume canonic
                {
                    // preluam numele de particula recursiv
                    String canonicalName = getParticlePointer(answerIndex, responseByteBuffer);
                    answerIndex += dataLength;
                    canonicalName = canonicalName.substring(0, canonicalName.length() - 1);
                    System.out.println(" | Nume canonic: " + canonicalName);
                } else // altfel, afisam datele asa cum sunt
                {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < dataLength; ++i) {
                        sb.append((char) (responseByteBuffer[answerIndex++] & 0xFF));
                    }
                    System.out.println(" | " + sb.toString());
                }
                ++recordIndex;
            }
        }
        catch (SocketTimeoutException soe)
        {
            System.out.println("Eroare: Server-ul DNS nu a raspuns la timp.");
            return null;
        }
        catch (ArrayIndexOutOfBoundsException aioobe) // pachet DNS corupt
        {
            return null;
        }
        return null;
    }

    private void printData(byte[] buffer) {
        for (int i = 0; i < buffer.length; ++i) {
            System.out.print('\t');
            System.out.printf("[0x%02X] ", buffer[i]);
            if ((i + 1) % 8 == 0) {
                System.out.println();
            }
        }
        System.out.println();
    }


    private String getParticlePointer(int pointerIndex, byte[] buffer)
    {
        if ((buffer[pointerIndex] & 0xFF) == 0x0) //conditia de stop a recursiei
        {
            return "";
        }

        if ((buffer[pointerIndex] & 0xFF) >= 192) // iar am gasit pointer
        {
            // calculam indicele de octet
            int newPointerIndex = ((buffer[pointerIndex] & 0x3F) << 8) | (buffer[pointerIndex + 1] & 0xFF);
            return getParticlePointer(newPointerIndex, buffer);
        }

        int currentNumberOfCharacters = buffer[pointerIndex++] & 0xFF;
        StringBuilder currentElement = new StringBuilder();
        for (int i = 0; i < currentNumberOfCharacters; ++i)
        {
            currentElement.append((char)(buffer[pointerIndex + i] & 0xFF));
        }

        pointerIndex += currentNumberOfCharacters;
        return (currentElement.toString() + "." + getParticlePointer(pointerIndex, buffer));
    }

}
