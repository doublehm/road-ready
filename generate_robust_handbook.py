
import fitz  # PyMuPDF
import os

def generate_robust_html(pdf_path, output_path, images_dir):
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
                padding: 15px;
                max-width: 800px;
                margin: 0 auto;
                background: #f9f9f9;
                color: #333;
            }
            .page-container {
                background: white;
                padding: 20px;
                margin-bottom: 20px;
                box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                border-radius: 8px;
                overflow: hidden; 
            }
            img {
                max-width: 100%;
                height: auto;
                display: block;
                margin: 15px auto;
                border: 1px solid #eee;
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
    
    print("Processing pages with advanced extraction...")
    
    for i, page in enumerate(doc):
        page_num = i + 1
        page_html = f'<div class="page-container" id="page-{page_num}">'
        
        # Get all text blocks
        text_blocks = page.get_text("blocks")
        
        # Get all image blocks
        # get_text("blocks") might miss vector graphics.
        # We will iterate through the display list to find drawings.
        
        # New Strategy: 
        # 1. Identify "Drawing Areas" (vector graphics).
        # 2. Render those areas as pixmaps.
        # 3. Extract standard images.
        # 4. Mix with text blocks.
        
        # Simplified for robustness:
        # Get the full page layout dict
        page_dict = page.get_text("dict")
        blocks = page_dict["blocks"]
        
        all_elements = []
        
        for b in blocks:
            # b['type']: 0 = text, 1 = image
            rect = fitz.Rect(b['bbox'])
            
            if b['type'] == 0: # Text
                # Check if it's mostly text
                text_content = ""
                for line in b["lines"]:
                    for span in line["spans"]:
                        text_content += span["text"] + " "
                
                all_elements.append({'type': 'text', 'rect': rect, 'content': text_content})
                
            elif b['type'] == 1: # Image
                # This is a bitmap image
                img_filename = f"p{page_num}_img_{len(all_elements)}.png"
                with open(os.path.join(images_dir, img_filename), "wb") as f:
                    f.write(b["image"])
                all_elements.append({'type': 'image', 'rect': rect, 'content': img_filename})

        # Capture Vector Graphics (Drawings) that aren't blocks
        # We inspect paths. If a large area has paths but no text blocks, it's a diagram.
        # This is complex. 
        
        # ALTERNATIVE: Use `get_drawings()`
        drawings = page.get_drawings()
        for draw in drawings:
            r = draw["rect"]
            # Heuristic: If a drawing is significant size, render that area as an image
            if r.width > 50 and r.height > 50:
                # Check if we already have an image covering this area
                covered = False
                for el in all_elements:
                    if el['type'] == 'image' and el['rect'].contains(r):
                        covered = True
                        break
                
                if not covered:
                    # Render this specific rect to an image
                    # We slightly expand rect to catch strokes
                    clip = r + (-2, -2, 2, 2)
                    
                    # Ensure clip is within page bounds
                    clip = clip & page.rect
                    
                    if clip.width > 1 and clip.height > 1: # Ensure non-zero and non-trivial
                        try:
                            pix = page.get_pixmap(clip=clip, dpi=150) # 150 dpi for good quality
                            draw_filename = f"p{page_num}_draw_{len(all_elements)}.png"
                            pix.save(os.path.join(images_dir, draw_filename))
                            all_elements.append({'type': 'image', 'rect': r, 'content': draw_filename})
                        except Exception as e:
                            print(f"Warning: Failed to render drawing on page {page_num}: {e}")

        # Remove duplicate/overlapping images (common in PDFs)
        # Sort by vertical position
        all_elements.sort(key=lambda x: x['rect'].y0)
        
        unique_elements = []
        for el in all_elements:
            # Filter logic could go here
            unique_elements.append(el)

        for el in unique_elements:
            if el['type'] == 'text':
                text = el['content'].strip()
                if text:
                    # Simple heuristic for headers
                    if text.isupper() and len(text) < 60:
                        page_html += f"<h3>{text}</h3>"
                    else:
                        page_html += f"<p>{text}</p>"
            else:
                page_html += f'<img src="/static/handbook_images/{el["content"]}" loading="lazy" />'
                
        page_html += f'<div class="page-number">Page {page_num}</div></div>'
        html_content += page_html

    html_content += "</body></html>"
    
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_content)
        
    print(f"Saved robust HTML to {output_path}")

if __name__ == "__main__":
    generate_robust_html(
        "app/static/driver-full.pdf", 
        "app/templates/handbook_content.html",
        "app/static/handbook_images"
    )
