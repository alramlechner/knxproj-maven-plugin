package name.lechners.knx.knxproj;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads a KNX ETS project file ({@code .knxproj}) and generates a Java source
 * file containing Calimero {@link tuwien.auto.calimero.GroupAddress} constants
 * for every group address defined in the project.
 *
 * <p><b>Example usage in a consuming project:</b>
 * <pre>{@code
 * <plugin>
 *   <groupId>dev.huebl.knx</groupId>
 *   <artifactId>knxproj-maven-plugin</artifactId>
 *   <version>1.0.0-SNAPSHOT</version>
 *   <executions>
 *     <execution>
 *       <goals><goal>generate</goal></goals>
 *       <configuration>
 *         <knxprojFile>${project.basedir}/src/main/knx/MyProject.knxproj</knxprojFile>
 *         <packageName>com.example.knx</packageName>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * <p>The generated class is automatically added to the compile source roots so
 * it is compiled along with the rest of the project.
 */
@Mojo(
    name            = "generate",
    defaultPhase    = LifecyclePhase.GENERATE_SOURCES,
    requiresProject = true,
    threadSafe      = true
)
public class GenerateGroupAddressesMojo extends AbstractMojo {

    /**
     * Path to the ETS project file ({@code .knxproj}).
     */
    @Parameter(property = "knxproj.file", required = true)
    private File knxprojFile;

    /**
     * Output directory for the generated Java source file.
     * The file is placed in the correct sub-directory according to
     * {@code packageName} automatically.
     */
    @Parameter(
        property       = "knxproj.outputDirectory",
        defaultValue   = "${project.build.directory}/generated-sources/knx"
    )
    private File outputDirectory;

    /**
     * Java package for the generated class, e.g. {@code com.example.knx}.
     */
    @Parameter(property = "knxproj.packageName", required = true)
    private String packageName;

    /**
     * Simple class name of the generated class.
     */
    @Parameter(property = "knxproj.className", defaultValue = "KNXGroupAddresses")
    private String className;

    /**
     * Fully-qualified class name of the Calimero GroupAddress type to use in
     * the generated code.  Change this if you use a forked or repackaged
     * version of Calimero.
     */
    @Parameter(
        property     = "knxproj.groupAddressClass",
        defaultValue = "tuwien.auto.calimero.GroupAddress"
    )
    private String groupAddressClass;

    /**
     * Skip the goal entirely.
     */
    @Parameter(property = "knxproj.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("knxproj:generate skipped (knxproj.skip=true)");
            return;
        }

        // ── Validate input ─────────────────────────────────────────────────────
        if (!knxprojFile.exists()) {
            throw new MojoExecutionException(
                    "KNX project file not found: " + knxprojFile.getAbsolutePath());
        }
        if (!knxprojFile.getName().endsWith(".knxproj")) {
            getLog().warn("knxprojFile does not have a .knxproj extension: " + knxprojFile.getName());
        }

        // ── Parse group addresses ──────────────────────────────────────────────
        getLog().info("Parsing KNX project file: " + knxprojFile.getAbsolutePath());
        List<GroupAddressEntry> entries;
        try {
            entries = new KnxProjectParser().parse(knxprojFile);
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Failed to parse KNX project file: " + e.getMessage(), e);
        }
        getLog().info("Found " + entries.size() + " group addresses.");

        // ── Generate Java source ───────────────────────────────────────────────
        String source = new JavaSourceGenerator().generate(
                entries,
                packageName,
                className,
                groupAddressClass,
                knxprojFile.getName(),
                LocalDateTime.now());

        // ── Write output file ──────────────────────────────────────────────────
        File targetFile = resolveTargetFile();
        targetFile.getParentFile().mkdirs();

        try (PrintWriter pw = new PrintWriter(targetFile, StandardCharsets.UTF_8)) {
            pw.print(source);
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Failed to write generated source: " + targetFile.getAbsolutePath(), e);
        }

        getLog().info("Generated: " + targetFile.getAbsolutePath());

        // ── Register source root ───────────────────────────────────────────────
        project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        getLog().debug("Added compile source root: " + outputDirectory.getAbsolutePath());
    }

    private File resolveTargetFile() {
        // Convert package name to directory path: com.example.knx → com/example/knx
        String packagePath = packageName.replace('.', File.separatorChar);
        return new File(outputDirectory, packagePath + File.separator + className + ".java");
    }
}
