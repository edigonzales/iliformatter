package ch.so.agi.pprint.webservice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import ch.so.agi.pprint.PrettyPrint;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class PrettyPrintController {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    
    private static final String UML_FLAVOR_PLANTUML = "plantuml";

    @Value("${app.ilidirs}")
    private String ilidirs;

    @GetMapping("/ping")
    public ResponseEntity<String> ping(@RequestHeader Map<String, String> headers, HttpServletRequest request) {
        headers.forEach((key, value) -> {
            log.info(String.format("Header '%s' = %s", key, value));
        });
        
        log.info("server name: " + request.getServerName());
        log.info("context path: " + request.getContextPath());
        log.info("ping"); 
        
        return new ResponseEntity<String>("iliprettyprint-web-service", HttpStatus.OK);
    }

    @PostMapping(value = "/api/prettyprint", consumes = {"multipart/form-data"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> pprint(@RequestPart(name = "file", required = true) MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }
        
        try {
            Path inDir = Files.createTempDirectory("pprint_input_");
            Path iliFile = inDir.resolve(file.getOriginalFilename());
            file.transferTo(iliFile);
            
            Path outDir = Files.createTempDirectory("pprint_output_");
            boolean ret = PrettyPrint.run(new File[] {iliFile.toFile()}, outDir, ilidirs, null);          
                       
            String iliPrettyPrinted = Files.readString(Paths.get(outDir.toString(), iliFile.getFileName().toString()));
           
            FileSystemUtils.deleteRecursively(inDir);
            FileSystemUtils.deleteRecursively(outDir);
            
            if (ret) {
                return ResponseEntity.ok(iliPrettyPrinted);
            } else {
                return ResponseEntity.internalServerError().body("error while converting ili file");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping(value = "/api/uml", consumes = {"multipart/form-data"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> uml(@RequestPart(name = "file", required = true) MultipartFile file, @RequestPart(name = "flavor", required = false) String flavor) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }
        
        if (flavor == null) {
            flavor = UML_FLAVOR_PLANTUML;
        }
        
        try {
            Path inDir = Files.createTempDirectory("uml_input_");
            Path iliFile = inDir.resolve(file.getOriginalFilename());
            file.transferTo(iliFile);
            
            Path outDir = Files.createTempDirectory("uml_output_");
            boolean ret = PrettyPrint.run(new File[] {iliFile.toFile()}, outDir, ilidirs, file.getOriginalFilename() + ".png");          
                       
            String iliPrettyPrinted = Files.readString(Paths.get(outDir.toString(), iliFile.getFileName().toString()));
                       
            byte[] imageBytes;
            
            try (InputStream in = Files.newInputStream(outDir.resolve(file.getOriginalFilename() + ".png"))) {
                imageBytes = in.readAllBytes();
            }

            FileSystemUtils.deleteRecursively(inDir);
            FileSystemUtils.deleteRecursively(outDir);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            
            if (ret) {
                return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            } else {
                return ResponseEntity.internalServerError().body("error while creating uml diagram");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
