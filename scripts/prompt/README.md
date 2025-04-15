## E-Wash
The E-wash script parses the oracles and tokens datasets to generate the input for the models.
To run the script, move to the `root` folder of the repository and execute the following command:

```shell
python3 ./scripts/preprocessing/ewash.py [path_to_dataset_file] [path_to_output_dir] {[path_to_configuration_file]}
```

where:

* `path_to_dataset_file` - is the path to the (oracles/tokens) dataset file to parse.
* `path_to_output_dir` - is the path to the output directory where to save the output file and the statistics.
* `path_to_configuration_file` - is the path to the e-wash configuration file.

The script will process the default E-Wash configuration file (in `./src/resources/ewash_config.json`), if not specified 
otherwise.