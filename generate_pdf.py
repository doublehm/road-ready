from fpdf import FPDF
import os

class PDF(FPDF):
    def header(self):
        self.set_font('helvetica', 'B', 16)
        self.set_text_color(0, 123, 255)
        self.cell(0, 10, 'Road Ready: Diagnostic Rides Extraction Report', ln=True, align='C')
        self.ln(10)

    def footer(self):
        self.set_y(-15)
        self.set_font('helvetica', 'I', 8)
        self.cell(0, 10, f'Page {self.page_no()}', 0, 0, 'C')

def generate_pdf():
    md_file = "extracted_data/diagnostic_rides_report.md"
    pdf_file = "extracted_data/diagnostic_rides_report.pdf"
    
    if not os.path.exists(md_file):
        print(f"Error: {md_file} not found.")
        return

    pdf = PDF()
    pdf.add_page()
    pdf.set_font("helvetica", size=12)

    with open(md_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for line in lines:
        line = line.strip()
        if not line:
            pdf.ln(5)
            continue
            
        if line.startswith("# "):
            # Already handled by header usually, but let's put it if it's the first
            continue
        elif line.startswith("## "):
            pdf.set_font("helvetica", 'B', 14)
            pdf.set_text_color(33, 37, 41)
            pdf.cell(0, 10, line[3:], ln=True)
            pdf.set_font("helvetica", size=12)
        elif line.startswith("### "):
            pdf.set_font("helvetica", 'B', 12)
            pdf.cell(0, 10, line[4:], ln=True)
            pdf.set_font("helvetica", size=12)
        elif line.startswith("#### "):
            pdf.set_font("helvetica", 'BI', 11)
            pdf.cell(0, 8, line[5:], ln=True)
            pdf.set_font("helvetica", size=12)
        elif line.startswith("- "):
            # Bullet points
            # Handle bold markdown **text** and problematic unicode
            clean_line = line[2:].replace("**", "").replace("\u2713", "[OK]").replace("\u2717", "[X]")
            pdf.set_x(15)
            pdf.multi_cell(0, 8, f"* {clean_line}")
        elif line.startswith("---"):
            pdf.line(10, pdf.get_y(), 200, pdf.get_y())
            pdf.ln(5)
        else:
            pdf.multi_cell(0, 8, line)

    pdf.output(pdf_file)
    print(f"Successfully generated PDF: {pdf_file}")

if __name__ == "__main__":
    generate_pdf()
