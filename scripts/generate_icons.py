#!/usr/bin/env python3
"""Generate favicon and icon PNGs from the Sudoku tile design."""
import struct
import zlib

def make_png(width, height, pixels):
    """Create a PNG file in memory from RGBA pixels."""
    def chunk(chunk_type, data):
        c = chunk_type + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    
    # IHDR
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    
    # IDAT
    raw = b''
    for y in range(height):
        raw += b'\x00'  # filter none
        for x in range(width):
            raw += bytes(pixels[y][x])
    
    return (b'\x89PNG\r\n\x1a\n' +
            chunk(b'IHDR', ihdr) +
            chunk(b'IDAT', zlib.compress(raw)) +
            chunk(b'IEND', b''))

def create_icon(size):
    """Create a Sudoku tile icon at the given size."""
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            # Background: dark navy
            r, g, b = 26, 26, 46
            a = 255
            
            # Grid lines (3x3)
            line_color = (74, 85, 104, 255)
            cell_size = size / 3
            if x % int(cell_size) < 1 or y % int(cell_size) < 1:
                r, g, b, a = line_color
            
            # One contrasting filled cell (center)
            cell_row = y // int(cell_size)
            cell_col = x // int(cell_size)
            if cell_row == 1 and cell_col == 1:
                r, g, b, a = 76, 201, 240, 255
            
            row.append((r, g, b, a))
        pixels.append(row)
    return pixels

# Generate 192x192
img192 = make_png(192, 192, create_icon(192))
with open('/home/ku7/git/nice_sudoku2/web/src/jsMain/resources/icon-192.png', 'wb') as f:
    f.write(img192)
print("Created icon-192.png")

# Generate 512x512
img512 = make_png(512, 512, create_icon(512))
with open('/home/ku7/git/nice_sudoku2/web/src/jsMain/resources/icon-512.png', 'wb') as f:
    f.write(img512)
print("Created icon-512.png")

# Generate apple-touch-icon (180x180)
img180 = make_png(180, 180, create_icon(180))
with open('/home/ku7/git/nice_sudoku2/web/src/jsMain/resources/apple-touch-icon.png', 'wb') as f:
    f.write(img180)
print("Created apple-touch-icon.png")

print("All icons generated successfully!")