import csv
import os
import sys

def split_csv(file_path, output_dir, rows_per_file=5, header_flag=False):
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    with open(file_path, 'r') as csv_file:
        reader = csv.reader(csv_file)
        headers = ""
        if header_flag:
            headers = next(reader)
        file_count = 0
        rows = []

        for i, row in enumerate(reader, start=1):
            rows.append(row)
            if i % rows_per_file == 0:
                output_file = os.path.join(output_dir, f'split_{file_count}.csv')
                with open(output_file, 'w', newline='') as output_csv:
                    writer = csv.writer(output_csv)
                    if header_flag:
                        writer.writerow(headers)
                    writer.writerows(rows)
                file_count += 1
                rows = []

        if rows:
            output_file = os.path.join(output_dir, f'split_{file_count}.csv')
            with open(output_file, 'w', newline='') as output_csv:
                writer = csv.writer(output_csv)
                if header_flag:
                    writer.writerow(headers)
                writer.writerows(rows)

if __name__ == "__main__":
    # Read absolute path to JSON file from command line arguments
    input_file_path = sys.argv[1]
    output_directory = sys.argv[2]
    elements_per_file = int(sys.argv[3]) if len(sys.argv) > 3 else 5
    header_flag = bool(sys.argv[4]) if len(sys.argv) > 4 else False
    # Split original CSV file into multiple files
    split_csv(input_file_path, output_directory, elements_per_file, header_flag)