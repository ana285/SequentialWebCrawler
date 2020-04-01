import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SequentialWebCrawler {
    private List<String> queue;
    private int computedLinks;
    private HashMap<String, RobotsParse> robots;
    private ClientHTTP clientHTTP;
    private ClientDNS clientDNS;
    private Integer maxRedirect = 3;
    private HashMap<String, HashSet<String>> digraph;


    public SequentialWebCrawler(){
        queue = new ArrayList<>();
        computedLinks = 0;
        robots = new HashMap<>();
        clientHTTP = new ClientHTTP();
        clientDNS = new ClientDNS();
    }

    public SequentialWebCrawler(String startLink){
        queue = new ArrayList<>();
        queue.add(startLink);
        computedLinks = 0;
        robots = new HashMap<>();
        clientHTTP = new ClientHTTP();
        clientDNS = new ClientDNS();
    }

    public String getWebPageContent(String url) throws IOException {
        URL website = new URL(url);
        URLConnection connection = website.openConnection();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream()));

        StringBuilder response = new StringBuilder();
        String inputLine;

        while ((inputLine = in.readLine()) != null)
            response.append(inputLine);

        in.close();

        return response.toString();
    }


    public void crawl(String jsonName) throws Exception {
        if(queue.isEmpty()){
            return;
        }

        digraph = readJsonDigraph(jsonName);

        long startTime = System.currentTimeMillis();
        while(!queue.isEmpty() && computedLinks < 2000){
            String url = queue.remove(0);
            System.out.println("\n\n\nUrl: " + url);
            System.out.println("\tCheck if visited.");

            URL website = new URL(url);
            String domain = website.getHost();
            String resource = website.getPath();

            File absPath = new File("./http/" + domain + resource);
            if (absPath.exists()) {
                System.out.println("This content already exists!");
                continue;
            }
//            if(visited.contains(url)){
//                continue;
//            }
            absPath = new File("./http/" + domain);
            if(!absPath.exists()){ //prima explorare a domeniului, nevoie de robots.txt
                RobotsParse robot = new RobotsParse(domain, "RIWEB_CRAWLER");
                boolean flagRobots = robot.parseRobots();
                if(flagRobots){ //robots.txt exista, se adauga in hashmap pentru verificari ulterioare
                    robots.put(url, robot);
                }
            }

            System.out.println("Resursa: " + resource);
            if(robots.get(url) == null){
                System.out.println("Robots not found! Checking again for them.");
                RobotsParse robot = new RobotsParse(domain, "RIWEB_CRAWLER");
                boolean flagRobots = robot.parseRobots();
                if(flagRobots){ //robots.txt exista, se adauga in hashmap pentru verificari ulterioare
                    robots.put(url, robot);
                }
            }

            if(robots.get(url) == null){
                System.out.println("Robots not taken. Leave the domain.");
                continue;
            }



            List<String> disallowed = robots.get(url).getDisallowed();
            System.out.println("Disallowed for " + url + " are: " + disallowed.toString() );

            if (!disallowed.isEmpty()) {
                boolean restricted = false;
                for (String restiction : disallowed) {
                    if (resource.startsWith(restiction)) {
                        System.out.println("Resource restricted from robots.txt!!! ");
                        restricted = true;
                        break;
                    }
                }
                if (restricted) {
                    continue;
                }
            }


            // daca s-a ajuns aici am trecut de robots.txt si avem acces la aceasta resursa.
            // se salveaza local

            if (resource.equals(""))
            {
                resource = "/";
            }

            String host = clientDNS.solveDomain(domain);
            if(host == null){
                try {
                    InetAddress giriAddress = java.net.InetAddress.getByName(url);
                    host = giriAddress.getHostAddress();
                    System.out.println(host);
                } catch (UnknownHostException e) {
                    System.out.println("Host not found!");
                    continue;
                }
            }
            WebContentInfo contentInfo = clientHTTP.getResourceContent(resource, domain, host, 80);
            boolean flagSuccess = true;
            boolean flag301 = false;
            String newDomain = null;

            if(contentInfo == null){
                continue;
            }

            if(contentInfo.getCode() != null && contentInfo.getCode().startsWith("30")){
                if(contentInfo.getCode().contains("301")) {
                    flag301 = true;
                }

                String newLocation = contentInfo.getRedirectLocation();
                Integer tries = maxRedirect;

                while(tries != 0) {
                    URL newWebsite = new URL(newLocation);
                    newDomain = newWebsite.getHost();

                    contentInfo = clientHTTP.getResourceContent(resource, newDomain, host, 80);
                    if (contentInfo.getCode().equals("200")) {
                        break;
                    } else if(contentInfo.getCode().startsWith("30")){
                        if(contentInfo.getCode().contains("301")) {
                            flag301 = true;
                        }
                        if((tries - 1) == 0) {
                            System.out.println("Resource couldn't be obtained. Spider trap.");
                            flagSuccess = false;
                        }
                        tries--;
                    } else {
                        System.out.println("Resource couldn't be obtained!");
                        flagSuccess = false;
                        break;
                    }
                }
            } else if(contentInfo.getCode() == null){
                continue;
            }

            if(!flagSuccess){
                continue;
            }

            if(flag301 && flagSuccess){
                renameDirectory("./http/" + domain, "./http/" + newDomain);
            }

            //System.out.println(url + "|" + content);
            // construim calea de salvare a resursei

            String extension = "";
            int i = resource.lastIndexOf('.');
            if (i > 0) {
                extension = resource.substring(i+1);
            }

            String path = "./http/" + domain + resource;
            if (extension.equals(""))
            {
                if (!path.endsWith("/"))
                {
                    path += "/";
                }
                path += "index.html";
            }

            File file = new File(path);
            File parentDirectory = file.getParentFile();
            if (!parentDirectory.exists())
            {
                parentDirectory.mkdirs();
            }

            // salvam resursa in calea construita
            BufferedWriter writer = new BufferedWriter(new FileWriter(path));
            writer.write(contentInfo.getContent());
            writer.close();
            System.out.println("Continutul salvat la " + path + "\n");
            computedLinks++;

            File input = new File(path);

            Document doc = Jsoup.parse(input, "UTF-8", url);

            Elements metaTags = doc.getElementsByTag("meta");

            for (Element metaTag : metaTags) {
                String content = "";
                String name = metaTag.attr("name");
                if(name.equals("robots")){
                    content = metaTag.attr("content");
                    System.out.println("\t\tMeta for "+ url + " : " + content);
                    if(content.contains("none")){
                        input.delete();
                        continue;
                    }
                    if(content.contains("nofollow")){
                        continue;
                    }
                    if(content.contains("noindex")){
                        input.delete();
                    }
                }
            }

            Elements as = doc.select("a");
            for (Element a : as) {
                System.out.println(a);
                String newUrl = a.absUrl("href");
                //newUrl = url + newUrl;

                System.out.println("Abs url:" + newUrl);

                if(newUrl.contains("#")){
                    newUrl = newUrl.substring(0, newUrl.indexOf("#"));
                }
                System.out.println("new url: " + newUrl);

                if(!newUrl .equals("")) {
                    URL website2 = new URL(newUrl);
                    String domain2 = website2.getHost();
                    String resource2 = website2.getPath();

                    System.out.print("Domain: " + domain2 + " | resource: " + resource2);
                    File absPath2 = new File("./http/" + domain2 + resource2);
                    if(!absPath2.exists()) {
                        if(!queue.contains(newUrl)) {
                            System.out.println(" added to queue! ");
                            queue.add(newUrl);
                            addToDigraph(url, newUrl);
                        }else{
                            System.out.println(" NOT added to queue! It already is in queue! ");
                        }
                    }else{
                        System.out.println(" NOT added to queue! It was already explored.");

                    }
                }
            }
        }

        writeJsonDigraph(jsonName);

        long endTime = System.currentTimeMillis();

        // get difference of two nanoTime values
        long timeElapsed = endTime - startTime;

        System.out.println("Execution time in seconds : " +
                TimeUnit.MILLISECONDS.toSeconds(timeElapsed));

    }

    public void renameDirectory(String initialNamePath, String newNamePath) {
        //create source File object
        File oldName = new File(initialNamePath);

        //create destination File object
        File newName = new File(newNamePath);

        boolean isFileRenamed = oldName.renameTo(newName);

        if(isFileRenamed)
            System.out.println("File has been renamed");
        else
            System.out.println("Error renaming the file");
    }

    private void writeJsonDigraph(String jsonName){
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(jsonName), digraph);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private HashMap<String, HashSet<String>> readJsonDigraph(String jsonName) {
        File jsonFile = new File(jsonName);

        if(jsonFile.exists()){
            ObjectMapper mapper = new ObjectMapper();
            HashMap<String, HashSet<String>> map = new HashMap<>();
            try {
                map = mapper.readValue(new File(
                        jsonName), new TypeReference<Map<String, HashSet>>() {
                });

                return map;

            } catch (Exception e) {
                e.printStackTrace();
            }finally{
                return map;
            }
        }else{
            return new HashMap<>();
        }
    }
    private void addToDigraph(String link, String linkRefered){
        if(digraph.containsKey(link)){
            HashSet<String> set = digraph.get(link);
            set.add(linkRefered);
            digraph.put(link, set);
        }else{
            HashSet<String> set = new HashSet();
            set.add(linkRefered);
            digraph.put(link, set);
        }
    }


}
