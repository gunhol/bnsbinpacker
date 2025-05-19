package one.hollander.bnsbinpacker;

import lombok.RequiredArgsConstructor;
import one.hollander.bnsbinpacker.service.BinService;
import one.hollander.bnsbinpacker.service.DatService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
@RequiredArgsConstructor
public class Application implements CommandLineRunner {

    private final DatService datService;
    private final BinService binService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 4) {
            var type = args[0];
            var command = args[1];
            var in = args[2];
            var out = args[3];

            switch (command) {
                case "unpack" -> {
                    if (StringUtils.equalsIgnoreCase(type, "dat")) {
                        String datafileOut = "datafile64.bin";
                        System.out.printf("Unpacking %s to %s...%n", in, datafileOut);
                        datService.extractDatafile64Bin(new File(in), new File(datafileOut));
                    } else {
                        System.out.printf("Unpacking %s to %s...%n", in, out);
                        binService.unpackBin(new File(in), new File(out), true);
                    }
                    System.out.println("Done.");
                }
                case "pack" -> {
                    if (!StringUtils.equalsIgnoreCase(type, "bin")) {
                        printUsage();
                        System.exit(1);
                    }
                    System.out.printf("Packing %s to %s...%n", in, out);
                    binService.repackBin(new File(in), new File(out), true);
                    System.out.println("Done.");
                }
                default -> {
                    printUsage();
                    System.exit(1);
                }
            }
        } else {
            printUsage();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar bnsdatpacker.jar dat unpack <binfile>
                  java -jar bnsdatpacker.jar bin unpack <binfile> <output-folder>
                  java -jar bnsdatpacker.jar bin pack <folder> <output-binfile>
                """);
    }
}
