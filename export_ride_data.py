import sqlite3
import csv
import json
import os

def extract_data():
    output_dir = "extracted_data"
    os.makedirs(output_dir, exist_ok=True)
    
    conn = sqlite3.connect('roadready.db')
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    cursor.execute("SELECT * FROM diagnostic_rides ORDER BY id DESC")
    rows = cursor.fetchall()
    
    if not rows:
        print("No diagnostic rides found.")
        return

    # 1. Export to CSV
    csv_file = os.path.join(output_dir, "diagnostic_rides.csv")
    with open(csv_file, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(rows[0].keys()) # Header
        for row in rows:
            writer.writerow(row)
    print(f"Exported CSV to {csv_file}")

    # 2. Export to Markdown Report
    md_file = os.path.join(output_dir, "diagnostic_rides_report.md")
    with open(md_file, 'w') as f:
        f.write("# Road Ready: Diagnostic Rides Extraction Report\n\n")
        f.write(f"This report contains a summary of {len(rows)} diagnostic sessions.\n\n")
        
        for row in rows:
            f.write(f"## Ride ID: {row['id']}\n")
            f.write(f"- **Start Time:** {row['start_time']}\n")
            f.write(f"- **Overall Score:** {row['overall_score']}/100\n")
            
            duration = row['duration_minutes'] if row['duration_minutes'] is not None else 0
            distance = row['distance_km'] if row['distance_km'] is not None else 0
            
            f.write(f"- **Duration:** {duration:.2f} minutes\n")
            f.write(f"- **Distance:** {distance:.2f} km\n")
            
            # Parse results JSON
            results_str = row['criteria_results']
            results = json.loads(results_str) if results_str else {}
            f.write("### Evaluation Criteria:\n")
            
            f.write("#### Criteria Met:\n")
            if results.get('criteria_met'):
                for item in results['criteria_met']:
                    f.write(f"- {item}\n")
            else:
                f.write("- None\n")
                
            f.write("#### Criteria Failed:\n")
            if results.get('criteria_failed'):
                for item in results['criteria_failed']:
                    f.write(f"- {item}\n")
            else:
                f.write("- None\n")

            # Include Coach Notes
            notes = row['evaluator_notes']
            if notes:
                f.write("### Coach Notes:\n")
                f.write(f"```\n{notes}\n```\n")

            # Include Human Feedback
            human_str = row['human_feedback']
            if human_str:
                human_data = json.loads(human_str)
                f.write("### Supervisor Feedback:\n")
                for item in human_data:
                    f.write(f"- **{item.get('label')} ({item.get('code')})**: {item.get('count')} times\n")
                    if item.get('timestamps'):
                        times = []
                        for t in item['timestamps']:
                            m = int(t['elapsed'] // 60)
                            s = int(t['elapsed'] % 60)
                            times.append(f"{m}:{s:02d}")
                        f.write(f"  *Timestamps:* {', '.join(times)}\n")
            
            f.write("\n---\n\n")
            
    print(f"Exported Markdown report to {md_file}")
    conn.close()

if __name__ == "__main__":
    extract_data()
