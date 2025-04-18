package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.preprocessing.components.Callable;
import star.llms.prompts.dataset.preprocessing.components.Clazz;
import star.llms.prompts.dataset.preprocessing.components.Field;

import java.util.List;

public class ClazzBuilder {

    private String identifier;
    private String packageIdentifier;
    private List<String> superClasses;
    private List<String> interfaces;
    private String filePath;
    private List<Field> fields;
    private List<Callable> constructors;
    private List<Callable> methods;

    public Clazz build() {
        return new Clazz(
                this.identifier,
                this.packageIdentifier,
                this.superClasses,
                this.interfaces,
                this.filePath,
                this.fields,
                this.constructors,
                this.methods
        );
    }

    /**
     * Set the name of the class.
     *
     * @param identifier the class name
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Set the package of the class.
     *
     * @param packageIdentifier the package of the class
     */
    public void setPackageIdentifier(String packageIdentifier) {
        this.packageIdentifier = packageIdentifier;
    }

    /**
     * Set the superclasses of the class.
     *
     * @param superClasses the list of classes extended by the class
     */
    public void setSuperClasses(List<String> superClasses) {
        this.superClasses = superClasses;
    }

    /**
     * Set the interfaces implemented by the class.
     *
     * @param interfaces the list of interfaces implemented by the class
     */
    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    /**
     * Set the file path of the class.
     *
     * @param filePath the file path of the class within the repository
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Set the fields of the class.
     *
     * @param fields the fields defined in the class
     */
    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    /**
     * Set the list of constructors defined in the class.
     *
     * @param constructors the constructors of the class
     */
    public void setConstructors(List<Callable> constructors) {
        this.constructors = constructors;
    }

    /**
     * Set the list of methods defined in the class.
     *
     * @param methods the methods of the class
     */
    public void setMethods(List<Callable> methods) {
        this.methods = methods;
    }
}
