import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class RobotsParse {
    private String domain;
    private List<String> disallowed;
    private ClientHTTP clientHttp;
    private ClientDNS clientDNS;
    private String robotName;


    public RobotsParse(String url, String name){
        this.domain = url;
        this.disallowed = new ArrayList<>();
        this.clientHttp = new ClientHTTP();
        this.clientDNS = new ClientDNS();
        this.robotName = name;
    }

    public List<String> getDisallowed() {
        return disallowed;
    }

    public boolean parseRobots(){
        try {//TODO get host from my DNS
            String adresaIp = clientDNS.solveDomain(domain);
            System.out.println("IP:" + adresaIp);
            WebContentInfo info = clientHttp.getResourceContent("/robots.txt", domain,  adresaIp, 80);

            if(info == null){
                return false;
            }

            if(info.getCode() != null && info.getCode().startsWith("30")){
                URL website = new URL(info.getRedirectLocation());
                String domain  = website.getHost();
                String resource = website.getPath();
                if(website.equals(domain + "/robots.txt")){
                    disallowed = null;
                    System.out.println("Robots redirected to the same address!");
                    return false;
                }
                info = clientHttp.getResourceContent(resource, domain, adresaIp, 80);
            }

            String robotsContent = info.getContent();

            if(robotsContent == null){
                return false;
            }

            System.out.println("Robots.txt:\n" + robotsContent);

            boolean flagUserAgent = false;

            Scanner scanner = new Scanner(robotsContent);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if(line.startsWith("User-agent: " + robotName)){
                    flagUserAgent = true;
                }

                if(flagUserAgent && line.equals("")){
                    break;
                }

                if(flagUserAgent){
                    if(line.startsWith("Disallow")){
                        String path = line.substring(line.lastIndexOf("Disallow: ") + 10);
                        System.out.println("Disallow pe " + robotName + " : " + path);
                        if(!path.trim().equals("")) {
                            disallowed.add(path);
                        }
                    }
                }
            }
            scanner.close();

            if(!flagUserAgent){
                scanner = new Scanner(robotsContent);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if(line.startsWith("User-agent: *")){
                        flagUserAgent = true;
                    }

                    if(flagUserAgent && line.equals("")){
                        break;
                    }

                    if(flagUserAgent){
                        if(line.startsWith("Disallow")){
                            String path = line.substring(line.lastIndexOf("Disallow: ") + 10);
                            System.out.println("Disallow pe *: " + path);
                            if(!path.trim().equals("")) {
                                disallowed.add(path);
                            }
                        }
                    }
                }
                scanner.close();
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Finals dissalow: " + disallowed.toString());

        return true;
    }


}
