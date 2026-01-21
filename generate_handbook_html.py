
import fitz  # PyMuPDF
import os

def generate_html(pdf_path, output_path):
    print(f"Opening {pdf_path}...")
    doc = fitz.open(pdf_path)
    
    html_content = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>BC Driver's Guide</title>
        <style>
            body { 
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                line-height: 1.6;
                padding: 20px;
                max-width: 800px;
                margin: 0 auto;
                background: #f9f9f9;
                color: #333;
            }
            .page-container {
                background: white;
                padding: 20px;
                margin-bottom: 30px;
                box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                border-radius: 8px;
                overflow: hidden; /* Prevent massive layout shifts */
            }
            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 10px auto;
            }
            h1, h2, h3 { color: #003366; margin-top: 1.5em; }
            p { margin-bottom: 1em; }
            .page-number {
                text-align: center;
                font-size: 0.8em;
                color: #999;
                margin-top: 20px;
                border-top: 1px solid #eee;
                padding-top: 10px;
            }
        </style>
    </head>
    <body>
    """
    
    print("Converting pages...")
    for i, page in enumerate(doc):
        # "text" block output is safer for responsive mobile reading than "html" (which uses absolute positioning)
        # However, "html" preserves images better.
        # Let's try "html" but strip the absolute positioning/fixed width styles to force responsiveness.
        # Actually, PyMuPDF's get_text("html") is strict. 
        
        # Better approach for "Reflowable": extract blocks and render them simply.
        blocks = page.get_text("blocks")
        
        page_html = f'<div class="page-container" id="page-{i+1}">'
        
        # Sort blocks by vertical position
        blocks.sort(key=lambda b: b[1])
        
        for b in blocks:
            # b = (x0, y0, x1, y1, "lines ...", block_no, block_type)
            # type 0 = text, 1 = image
            if b[6] == 0: # Text
                text = b[4].replace('\n', ' ')
                if text.strip():
                    # Simple heuristic for headers
                    if b[4].isupper() and len(b[4]) < 50:
                        page_html += f"<h3>{text}</h3>"
                    else:
                        page_html += f"<p>{text}</p>"
            elif b[6] == 1: # Image
                # We can't easily extract inline images without saving them to disk first.
                # For this script, we'll skip inline image extraction to keep it simple and fast,
                # or we rely on the PDF viewer for the visual-heavy parts.
                # To make it "natural", text flow is key.
                pass
                
        page_html += f'<div class="page-number">Page {i+1}</div></div>'
        html_content += page_html

    html_content += "</body></html>"
    
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_content)
        
    print(f"Saved to {output_path}")

if __name__ == "__main__":
    generate_html("app/static/driver-full.pdf", "app/templates/handbook_content.html")
