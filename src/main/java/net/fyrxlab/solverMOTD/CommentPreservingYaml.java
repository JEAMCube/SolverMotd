package net.fyrxlab.solverMOTD;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class CommentPreservingYaml {
    private final Yaml yaml;

    public CommentPreservingYaml() {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        // Se utiliza un Representer personalizado para controlar el estilo de los scalars
        this.yaml = new Yaml(new CommentRepresenter(), options);
    }

    /**
     * Carga el contenido YAML de un archivo preservando la estructura en un Map.
     *
     * @param file Archivo YAML a cargar.
     * @return Map con los datos del YAML.
     * @throws IOException Si ocurre algún error de entrada/salida.
     */
    public Map<String, Object> load(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            Object loaded = yaml.load(fis);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            } else {
                return new LinkedHashMap<>();
            }
        }
    }

    /**
     * Guarda el contenido del Map en un archivo YAML, añadiendo un header (comentarios) si se proporciona.
     *
     * @param data   Datos a guardar.
     * @param file   Archivo de destino.
     * @param header Cadena de header (comentarios) a incluir al inicio.
     * @throws IOException Si ocurre algún error de escritura.
     */
    public void save(Map<String, Object> data, File file, String header) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            if (header != null && !header.isEmpty()) {
                writer.write(header);
                writer.write(System.lineSeparator());
            }
            yaml.dump(data, writer);
        }
    }

    /**
     * Realiza la fusión (merge) entre el mapa de valores por defecto y el mapa actual,
     * añadiendo las claves que no existen en el mapa actual.
     *
     * @param defaults Mapa con los valores por defecto.
     * @param current  Mapa actual a actualizar.
     */
    public void mergeMaps(Map<String, Object> defaults, Map<String, Object> current) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!current.containsKey(key)) {
                current.put(key, value);
            } else if (value instanceof Map && current.get(key) instanceof Map) {
                // Fusión recursiva de sub-mapas
                mergeMaps((Map<String, Object>) value, (Map<String, Object>) current.get(key));
            }
        }
    }

    /**
     * Representer personalizado para controlar el estilo de los scalars.
     * Utiliza el estilo LITERAL (representado por '|') si el valor contiene saltos de línea,
     * de lo contrario utiliza el estilo PLAIN (null).
     */
    private static class CommentRepresenter extends Representer {
        @Override
        protected Node representScalar(Tag tag, String value, Character style) {
            Character newStyle = value.contains("\n") ? '|' : null;
            return super.representScalar(tag, value, newStyle);
        }
    }
}
