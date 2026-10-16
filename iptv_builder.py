import os
import re
import sys
import gzip
import argparse
import requests
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone

# ==========================================
# 1. YOUR CONFIGURATION
# ==========================================
M3U_SOURCE = "2ndStrong8k_edit.txt" # Change to your actual .m3u file path or URL
EPG_URL = "https://opop.pro/XNawShQyV2ofdW/?gzip=1" # Replace with your EPG source

# Add any keywords here. The script checks inside the group-title="" tag.
# Using lower case is required here.
UNWANTED_CATEGORIES = [
    "uk", "arab", "india", "hindi", "punjabi", 
    "bangla", "pakistan", "latino", "vod", "catchup"
]

# Output Files
CLEAN_M3U = "clean_playlist.m3u"
CLEAN_EPG = "clean_epg.xml"

# Dynamic Sports Settings
APPEND_LIVE_SCORES = True
NBA_STREAM_URL = "http://line.glawoqa.com/live/xxxxx/xxxxx/nba-live.ts"
NFL_STREAM_URL = "http://line.glawoqa.com/live/xxxxx/xxxxx/nfl-live.ts"

# ==========================================
# 2. HELPER FUNCTIONS
# ==========================================
def check_active_games():
    """Pings ESPN to check if any NBA or NFL game is actively playing right now."""
    print("Checking for active live sports...")
    for sport in ["basketball/nba", "football/nfl"]:
        try:
            url = f"https://site.api.espn.com/apis/site/v2/sports/{sport}/scoreboard"
            data = requests.get(url, timeout=5).json()
            for event in data.get('events', []):
                if event.get('status', {}).get('type', {}).get('state') == 'in':
                    return True
        except Exception:
            pass
    return False

def download_and_extract_epg(url, temp_xml_path="temp_epg.xml"):
    print(f"Downloading EPG from {url}...")
    response = requests.get(url, stream=True)
    response.raise_for_status()
    
    if url.endswith('.gz') or 'gzip=' in url.lower():
        with open(temp_xml_path, 'wb') as f_out:
            f_out.write(gzip.decompress(response.content))
    else:
        with open(temp_xml_path, 'wb') as f_out:
            f_out.write(response.content)
    return temp_xml_path

def fetch_dynamic_events(sport_path, prefix, stream_url, default_duration_hours=3.0, sport_label="Sports"):
    """Fetches ESPN API games and generates dynamic M3U and XMLTV blocks."""
    url = f"https://site.api.espn.com/apis/site/v2/sports/{sport_path}/scoreboard"
    try:
        response = requests.get(url, timeout=10)
        data = response.json()
    except Exception:
        return [], [], []

    xml_channels, xml_programmes, m3u_lines = [], [], []
    
    for event in data.get('events', []):
        event_id = event.get('id', 'unknown')
        base_name = event.get('name', f'{sport_label} Game')
        date_str = event.get('date') 
        chan_id = f"{prefix}-{event_id}"
        
        broadcast_names = []
        logo_url = ""
        competitions = event.get('competitions', [])
        
        status = event.get('status', {})
        state = status.get('type', {}).get('state', 'pre') 
        status_detail = status.get('type', {}).get('detail', '') 
        
        home_score, away_score = "0", "0"
        
        if competitions:
            for b in competitions[0].get('broadcasts', []):
                broadcast_names.extend(b.get('names', []))
            
            competitors = competitions[0].get('competitors', [])
            for comp in competitors:
                if comp.get('homeAway') == 'home':
                    logo_url = comp.get('team', {}).get('logo', '')
                    home_score = comp.get('score', '0')
                else:
                    away_score = comp.get('score', '0')
            if not logo_url and competitors:
                logo_url = competitors[0].get('team', {}).get('logo', '')
                
        network_str = f" | TV: {', '.join(broadcast_names)}" if broadcast_names else ""
        display_name = base_name

        if APPEND_LIVE_SCORES and state in ['in', 'post']:
            display_name = f"[{away_score}-{home_score} | {status_detail}] {base_name}"

        if date_str:
            start_time = datetime.strptime(date_str, "%Y-%m-%dT%H:%M:%SZ") if len(date_str) > 17 else datetime.strptime(date_str, "%Y-%m-%dT%H:%MZ"); start_time = start_time.replace(tzinfo=timezone.utc)
            end_time = start_time + timedelta(hours=default_duration_hours)
            
            start_xmltv = start_time.strftime("%Y%m%d%H%M%S +0000")
            end_xmltv = end_time.strftime("%Y%m%d%H%M%S +0000")
            
            # Generate XML & M3U Data
            chan_elem = ET.Element("channel", id=chan_id)
            ET.SubElement(chan_elem, "display-name").text = display_name
            if logo_url: ET.SubElement(chan_elem, "icon", src=logo_url)
            xml_channels.append(chan_elem)
            
            prog_elem = ET.Element("programme", start=start_xmltv, stop=end_xmltv, channel=chan_id)
            ET.SubElement(prog_elem, "title", lang="en").text = display_name
            ET.SubElement(prog_elem, "desc", lang="en").text = f"{display_name}{network_str}"
            ET.SubElement(prog_elem, "category", lang="en").text = sport_label
            xml_programmes.append(prog_elem)
            
            logo_attr = f' tvg-logo="{logo_url}"' if logo_url else ''
            m3u_lines.append(f'#EXTINF:0 tvg-id="{chan_id}" tvg-name="{display_name}"{logo_attr} group-title="|NA| {sport_label} LIVE",{display_name}\n')
            m3u_lines.append(f'{stream_url}\n')
            
    return xml_channels, xml_programmes, m3u_lines

# ==========================================
# 3. MAIN PIPELINE
# ==========================================
def process_pipeline(force=False):
    if not force and not check_active_games():
        print("No active games playing right now. Exiting to save resources.")
        sys.exit(0)
        
    print("Proceeding with full M3U/EPG generation...")
    valid_tvg_ids = set()
    
    # --- PHASE 1: Parse and Filter M3U ---
    print("Filtering M3U using targeted group-title parsing...")
    with open(M3U_SOURCE, 'r', encoding='utf-8') as infile, \
         open(CLEAN_M3U, 'w', encoding='utf-8') as outfile:
        
        write_chunk = False
        chunk = []
        
        for line in infile:
            if line.startswith('#EXTINF'):
                # Isolate the group-title tag accurately
                group_match = re.search(r'group-title="([^"]+)"', line)
                group_title = group_match.group(1).lower() if group_match else ""
                
                # Check against unwanted categories
                if any(cat in group_title for cat in UNWANTED_CATEGORIES):
                    write_chunk = False
                else:
                    write_chunk = True
                    # Grab TVG-ID so we know what EPG data to keep later
                    id_match = re.search(r'tvg-id="([^"]+)"', line)
                    if id_match and id_match.group(1):
                        valid_tvg_ids.add(id_match.group(1))
                        
                chunk = [line]
                
            elif line.startswith('http'):
                chunk.append(line)
                if write_chunk:
                    outfile.writelines(chunk)
                chunk = []
                
            elif line.startswith('#EXTM3U'):
                outfile.write(line)
        
        # Inject dynamic live sports channels at the end
        nba_chans, nba_progs, nba_m3u = fetch_dynamic_events("basketball/nba", "nba", NBA_STREAM_URL, 3.0, "NBA")
        nfl_chans, nfl_progs, nfl_m3u = fetch_dynamic_events("football/nfl", "nfl", NFL_STREAM_URL, 3.5, "NFL")
        
        if nba_m3u: outfile.writelines(["\n"] + nba_m3u)
        if nfl_m3u: outfile.writelines(["\n"] + nfl_m3u)

    print(f"M3U filtered successfully. Generated {len(nba_chans) + len(nfl_chans)} dynamic live event channels.")

    # --- PHASE 2: Prune and Align XMLTV EPG ---
    temp_epg = download_and_extract_epg(EPG_URL)
    print("Aligning standard EPG data...")
    tree = ET.parse(temp_epg)
    root = tree.getroot()

    channels_to_remove = [c for c in root.findall('channel') if c.get('id') not in valid_tvg_ids]
    programmes_to_remove = [p for p in root.findall('programme') if p.get('channel') not in valid_tvg_ids]

    for channel in channels_to_remove: root.remove(channel)
    for programme in programmes_to_remove: root.remove(programme)

    # Inject dynamic XML elements
    for chan in reversed(nba_chans + nfl_chans): root.insert(0, chan)
    for prog in (nba_progs + nfl_progs): root.append(prog)

    tree.write(CLEAN_EPG, encoding='utf-8', xml_declaration=True)
    os.remove(temp_epg)
    
    print(f"Pipeline complete! Saved {CLEAN_M3U} and {CLEAN_EPG}.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="Run pipeline even if no live games exist")
    args = parser.parse_args()
    process_pipeline(force=args.force)
