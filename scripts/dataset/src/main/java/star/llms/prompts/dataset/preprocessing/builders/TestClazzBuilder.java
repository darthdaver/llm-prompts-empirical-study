package star.llms.prompts.dataset.preprocessing.builders;

import star.llms.prompts.dataset.preprocessing.components.*;
import star.llms.prompts.dataset.preprocessing.components.Callable;
import star.llms.prompts.dataset.preprocessing.components.Field;

import java.util.List;

public class TestClazzBuilder {

    private String identifier;
    private String packageIdentifier;
    private List<String> superClasses;
    private List<String> interfaces;
    private String filePath;
    private List<Field> fields;
    private List<Callable> auxiliaryMethods;
    private List<Callable> setupTearDownMethods;
    private List<Callable> testCases;

    public TestClazz build() {
        return new TestClazz(
                this.identifier,
                this.packageIdentifier,
                this.superClasses,
                this.interfaces,
                this.filePath,
                this.fields,
                this.auxiliaryMethods,
                this.setupTearDownMethods,
                this.testCases
        );
    }

    /**
     * Set the name of the test class.
     *
     * @param identifier the test class name
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Set the package of the test class.
     *
     * @param packageIdentifier the package of the test class
     */
    public void setPackageIdentifier(String packageIdentifier) {
        this.packageIdentifier = packageIdentifier;
    }

    /**
     * Set the superclasses of the test class.
     *
     * @param superClasses the list of classes extended by the test class
     */
    public void setSuperClasses(List<String> superClasses) {
        this.superClasses = superClasses;
    }

    /**
     * Set the interfaces implemented by the test class.
     *
     * @param interfaces the list of interfaces implemented by the test class
     */
    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    /**
     * Set the file path of a class.
     *
     * @param filePath the file path of the test class within the repository
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Set the fields of a class.
     *
     * @param fields the fields defined in the test class
     */
    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    /**
     * Set the methods of a class.
     *
     * @param methods the class methods
     */
    public void setAuxiliaryMethods(List<Callable> methods) {
        this.auxiliaryMethods = methods;
    }

    /**
     * Set the setup and tear down methods of the test class.
     *
     * @param methods the setup and tear down methods
     */
    public void setSetupTearDownMethods(List<Callable> methods) {
        this.setupTearDownMethods = methods;
    }

    /**
     * Set the test cases of the test class.
     *
     * @param testCases the test cases defined in the test class
     */
    public void setTestCases(List<Callable> testCases) {
        this.testCases = testCases;
    }
}
