import java.io.*;
import java.net.*;
import java.util.Map;

//import org.apache.http.client.HttpClient;

public class ClientHTTP {

    public boolean getResource(String resource, String domain, String host, int port) throws Exception {
        boolean responseFlag = false;

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 1000);

        boolean autoflush = true;

        PrintWriter out = new PrintWriter(socket.getOutputStream(), autoflush);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // send an HTTP request to the web server
        //301, 302, 307
        out.println("GET " + resource + " HTTP/1.1");
        out.println("Host: " + domain);
        out.println("User-Agent: RIWEB_CRAWLER");
        out.println("Connection: Close");
        out.println();

        System.out.println("\nCerere trimisa catre " + host + " la portul " + port + ".");

        // preluam raspunsul de la server
        System.out.println("Raspuns de la server: \n");

        String responseLine;
        responseLine = in.readLine();
        if (responseLine.contains("200 OK")) {
            responseFlag = true;
        } else if(responseLine.contains("301 Moved Permanently")){

        }

        System.out.println(responseLine);

        while ((responseLine = in.readLine()) != null) {
            if (responseLine.equals("")) {// header parcurs. Urmeaza pagina raspuns
                break;
            }
            System.out.println(responseLine);
        }

        if (responseFlag == true) {
            StringBuilder sb = new StringBuilder(); //am citit pagina
            while ((responseLine = in.readLine()) != null) {
                sb.append(responseLine + System.lineSeparator());
            }

            // construim calea de salvare a resursei
            String path = "./http/" + domain + resource;
            if (!path.endsWith(".html")) {
                if (!path.endsWith("/")) {
                    path += "/";
                }
                path += "index.html";
            }

            File file = new File(path);
            File parentDirectory = file.getParentFile();
            if (!parentDirectory.exists()) {
                parentDirectory.mkdirs();
            }

            // salvam resursa in calea construita
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));
            writer.write(sb.toString());
            writer.close();
            System.out.println("\nContinutul salvat.");
        }

        socket.close();
        return responseFlag;
    }

    public WebContentInfo getResourceContent(String resource, String domain, String host, int port){
        boolean responseFlag = false;
        WebContentInfo contentInfo = new WebContentInfo();

        try {
            Socket socket = new Socket(host, port);
            boolean autoflush = true;
            PrintWriter out = new PrintWriter(socket.getOutputStream(), autoflush);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // send an HTTP request to the web server
            //301, 302, 307
            out.println("GET " + resource + " HTTP/1.1");
            out.println("Host: " + domain);
            out.println("User-Agent: RIWEB_CRAWLER");
            out.println("Connection: Close");
            out.println();

            System.out.println("Cererea pentru resursa: " + resource + " la domeniul: " + domain);
            System.out.println("\nCerere trimisa catre " + host + " la portul " + port + ".");

            // preluam raspunsul de la server
            System.out.println("Raspuns de la server: \n");

            String responseLine;
            responseLine = in.readLine();
            if(responseLine == null){
                return null;
            }
            if (responseLine.contains("200 OK")) {
                contentInfo.setCode("200");
                responseFlag = true;
            }else if(responseLine.contains("30")){
                contentInfo.setCode(responseLine.substring(responseLine.lastIndexOf("30"), responseLine.lastIndexOf("30") + 3));
            }
            System.out.println(responseLine);

            while ((responseLine = in.readLine()) != null) {
                if (responseLine.equals("")){ // header parcurs. Urmeaza pagina raspuns
                    break;
                }if(responseLine.startsWith("Location: ")){
                    contentInfo.setRedirectLocation(responseLine.substring(responseLine.lastIndexOf("Location: ") + 10));
                }
                System.out.println(responseLine);
            }

            if (responseFlag == true) {
                StringBuilder sb = new StringBuilder(); //am citit pagina
                while ((responseLine = in.readLine()) != null) {
                    sb.append(responseLine + System.lineSeparator());
                }
                contentInfo.setContent(sb.toString());
            }

            socket.close();
        }catch(Exception e){
            System.out.println(e.getMessage());
            return null;
        }



        //System.out.println(contentInfo.toString());
        return contentInfo;
    }


}
