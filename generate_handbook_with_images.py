
import fitz  # PyMuPDF
import os

def generate_html_with_images(pdf_path, output_path, images_dir):
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
    
    print("Converting pages and extracting images...")
    
    for i, page in enumerate(doc):
        page_num = i + 1
        page_html = f'<div class="page-container" id="page-{page_num}">'
        
        # 1. Extract Images
        image_list = page.get_images()
        
        # We need to insert images relative to text. 
        # get_text("blocks") returns text blocks but not image position in the same list easily mixed.
        # Strategy: Use get_text("html") but clean it up, OR insert images at the top/bottom of blocks.
        
        # Simpler robust approach: get text blocks, and assume images are between paragraphs? 
        # No, layout is complex.
        
        # Best approach for fidelity + reflow:
        # Use PyMuPDF's `get_text("html")` but strip fixed width/height containers?
        # No, that's what I avoided before.
        
        # Let's save images and insert them based on their vertical position (y).
        
        blocks = page.get_text("blocks")
        # blocks: (x0, y0, x1, y1, text, block_no, block_type)
        
        # Collect image blocks too (PyMuPDF treats them as blocks if we use a different method, 
        # but standard get_text("blocks") might ignore drawings.
        # Actually, get_text("blocks") returns images if they are in the content stream.
        
        # Let's trust standard text extraction but insert ALL images found on the page at the bottom or top?
        # That breaks context.
        
        # Alternative: Render the page to an image? No, that's not reflowable.
        
        # Let's iterate through page items (text and images) by position.
        
        # Extract images to disk
        page_images = []
        for img_index, img in enumerate(image_list):
            xref = img[0]
            base_image = doc.extract_image(xref)
            image_bytes = base_image["image"]
            image_ext = base_image["ext"]
            image_filename = f"page_{page_num}_img_{img_index}.{image_ext}"
            
            with open(os.path.join(images_dir, image_filename), "wb") as f:
                f.write(image_bytes)
            
            # We need the rect of the image to sort it with text
            # rects = page.get_image_rects(xref) # returns list of Rect
            # We'll take the first occurrence
            rects = page.get_image_rects(xref)
            if rects:
                page_images.append((rects[0], image_filename))

        # Combine text blocks and images
        all_elements = []
        
        for b in blocks:
            # (x0, y0, x1, y1, text, ...)
            rect = fitz.Rect(b[0], b[1], b[2], b[3])
            all_elements.append({'type': 'text', 'rect': rect, 'content': b[4]})
            
        for img_rect, filename in page_images:
            all_elements.append({'type': 'image', 'rect': img_rect, 'content': filename})
            
        # Sort by vertical position (y0)
        all_elements.sort(key=lambda x: x['rect'].y0)
        
        for el in all_elements:
            if el['type'] == 'text':
                text = el['content'].replace('\n', ' ')
                if text.strip():
                    if el['content'].isupper() and len(el['content']) < 50:
                        page_html += f"<h3>{text}</h3>"
                    else:
                        page_html += f"<p>{text}</p>"
            else:
                # Image
                page_html += f'<img src="/static/handbook_images/{el["content"]}" loading="lazy" />'
                
        page_html += f'<div class="page-number">Page {page_num}</div></div>'
        html_content += page_html

    html_content += "</body></html>"
    
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(html_content)
        
    print(f"Saved HTML to {output_path}")

if __name__ == "__main__":
    generate_html_with_images(
        "app/static/driver-full.pdf", 
        "app/templates/handbook_content.html",
        "app/static/handbook_images"
    )
