
import os
import fitz  # PyMuPDF
from ebooklib import epub

def convert_pdf_to_epub(pdf_path, epub_path):
    print(f"Opening {pdf_path}...")
    doc = fitz.open(pdf_path)
    
    book = epub.EpubBook()
    book.set_identifier('road-ready-bc')
    book.set_title('Learn to Drive Smart (BC)')
    book.set_language('en')
    
    spine = ['nav']
    
    print(f"Converting {len(doc)} pages...")
    
    # We will combine pages into chapters to avoid having 100+ tiny HTML files
    # A simple heuristic: grouping every 10 pages, or just one massive file (can be slow).
    # Let's try one file per page for fidelity, or grouping if it's too much.
    # Grouping by 10 pages is a good balance.
    
    chunk_size = 10
    total_pages = len(doc)
    
    for i in range(0, total_pages, chunk_size):
        chunk_end = min(i + chunk_size, total_pages)
        chapter_content = ""
        
        for page_num in range(i, chunk_end):
            page = doc.load_page(page_num)
            # "html" output preserves layout using CSS
            # "text" is plain text
            # "xhtml" is semantic text
            # "blocks" gives us control
            
            # Use 'html' to preserve the exact layout as much as possible
            # This creates a lot of absolute positioning divs
            page_html = page.get_text("html")
            
            # Clean up full HTML tags to merge into one body
            # Removing basic HTML/Body tags to embed in our chapter
            page_content = page_html.replace('<!DOCTYPE html>', '').replace('<html>', '').replace('</html>', '')
            
            # Add a page separator
            chapter_content += f'<div id="page-{page_num+1}" class="pdf-page-container">{page_content}</div><hr class="page-break"/>'

        # Create Chapter
        chapter_title = f"Pages {i+1}-{chunk_end}"
        chapter_file = f"chapter_{i//chunk_size}.xhtml"
        
        c = epub.EpubHtml(title=chapter_title, file_name=chapter_file, lang='en')
        c.content = f"<h1>{chapter_title}</h1>{chapter_content}"
        
        book.add_item(c)
        spine.append(c)

    # Styling to ensure responsiveness
    # The extracted HTML often has fixed widths. We try to override some here.
    style = '''
        body { font-family: sans-serif; }
        .pdf-page-container { 
            position: relative; 
            overflow: hidden; 
            width: 100%;
        }
        /* Override absolute positioning if we want true reflow, 
           but PyMuPDF HTML output relies on it for layout preservation.
           If we want "flexible", we might need to zoom the container instead.
        */
        div { max-width: 100% !important; }
        img { max-width: 100%; height: auto; }
    '''
    nav_css = epub.EpubItem(uid="style_nav", file_name="style/nav.css", media_type="text/css", content=style)
    book.add_item(nav_css)

    book.toc = (epub.Link('chapter_0.xhtml', 'Start Reading', 'start'),)
    book.add_item(epub.EpubNcx())
    book.add_item(epub.EpubNav())
    book.spine = spine

    print(f"Writing EPUB to {epub_path}...")
    epub.write_epub(epub_path, book, {})
    print("Conversion Complete!")

if __name__ == "__main__":
    convert_pdf_to_epub("app/static/driver-full.pdf", "app/static/driver-full.epub")
