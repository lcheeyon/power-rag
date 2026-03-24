package com.powerrag.ingestion.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.powerrag.ingestion.model.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JavaSourceParser implements DocumentParser {

    @Override
    public String supportedExtension() { return "java"; }

    @Override
    public List<ParsedSection> parse(InputStream input, String fileName) {
        List<ParsedSection> sections = new ArrayList<>();
        try {
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            JavaParser parser = new JavaParser();
            var result = parser.parse(source);

            if (result.isSuccessful() && result.getResult().isPresent()) {
                CompilationUnit cu = result.getResult().get();
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString()).orElse("");

                for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    String className = cls.getNameAsString();

                    // One section per method
                    for (MethodDeclaration method : cls.getMethods()) {
                        String methodBody = method.toString();
                        if (methodBody.isBlank()) continue;

                        Map<String, Object> meta = new HashMap<>();
                        meta.put("file_name", fileName);
                        meta.put("doc_type", "JAVA");
                        meta.put("class_name", className);
                        meta.put("method_name", method.getNameAsString());
                        meta.put("section", className + "#" + method.getNameAsString());
                        if (!packageName.isBlank()) meta.put("package", packageName);
                        method.getBegin().ifPresent(pos -> meta.put("line_number", pos.line));

                        sections.add(ParsedSection.builder()
                                .text(methodBody)
                                .metadata(meta)
                                .build());
                    }

                    // Class-level section (fields, class javadoc) if no methods or as fallback
                    if (cls.getMethods().isEmpty()) {
                        Map<String, Object> meta = new HashMap<>();
                        meta.put("file_name", fileName);
                        meta.put("doc_type", "JAVA");
                        meta.put("class_name", className);
                        meta.put("section", className);
                        if (!packageName.isBlank()) meta.put("package", packageName);
                        sections.add(ParsedSection.builder()
                                .text(cls.toString())
                                .metadata(meta)
                                .build());
                    }
                }

                // Fallback: no classes found, treat entire file as one section
                if (sections.isEmpty()) {
                    sections.add(ParsedSection.builder()
                            .text(source)
                            .metadata(Map.of("file_name", fileName, "doc_type", "JAVA",
                                    "section", fileName))
                            .build());
                }
            } else {
                log.warn("JavaParser could not parse '{}', falling back to raw text", fileName);
                sections.add(ParsedSection.builder()
                        .text(source)
                        .metadata(Map.of("file_name", fileName, "doc_type", "JAVA",
                                "section", fileName))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to parse Java source '{}': {}", fileName, e.getMessage());
        }
        return sections;
    }
}
