package org.ymz.app.ai.tools;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 工具禁止修改的模板底座路径。
 *
 * @author ymz
 */
public class ProtectedTemplatePaths {

        private static final Set<String> PATHS = Set.of(
                        "App.jsx",
                        "main.jsx",
                        "index.css",
                        "components/ui",
                        "lib/utils.js",
                        "hooks/use-mobile.js"
        );

        public static boolean contains(String normalizedRelativePath) {
                return PATHS.stream()
                                .anyMatch(item -> normalizedRelativePath.equals(item)
                                                || normalizedRelativePath.startsWith(item + "/"));
        }

        public static String listText() {
                return PATHS.stream()
                                .sorted()
                                .map(path -> "- " + path)
                                .collect(Collectors.joining("\n"));
        }
}
