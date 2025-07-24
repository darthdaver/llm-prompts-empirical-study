import csv
import sys


if __name__ == "__main__":
    input_file = sys.argv[1]
    output_file = sys.argv[2]
    print(input_file)
    with open(input_file, 'r', newline='', encoding='utf-8') as csvfile:
        reader = csv.reader(csvfile)
        with open(output_file, 'w', encoding='utf-8') as outfile:
            file_content = ""
            for row in reader:
                row_str = "\n---COLUMN END---\n".join(row)
                row_str += "\n---ROW END---\n"
                file_content += row_str
            outfile.write(file_content)