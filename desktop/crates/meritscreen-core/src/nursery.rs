//! Nursery / early-learner curriculum (ages 3–6) — port of Android `NurseryStaticCatalog`
//! (5 playlists, 16 videos, 5 teach tracks, 52 cards) and the diverse picking rules of
//! `NurseryCurriculumRepository`. The bundled catalog works fully offline; a Firestore
//! `nurseryCurriculum` override can replace it later without changing call sites.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NurseryVideo {
    pub video_id: String,
    pub title: String,
    pub max_seconds: u32,
    pub channel: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NurseryPlaylist {
    pub id: String,
    pub title: String,
    pub category: String,
    pub videos: Vec<NurseryVideo>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NurseryTeachItem {
    pub id: String,
    pub tag: String,
    pub title: String,
    pub subtitle: String,
    pub emoji: String,
    pub speech_text: String,
    pub color_hex: String,
    #[serde(default)]
    pub count: Option<u32>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NurseryTrack {
    pub id: String,
    pub title: String,
    pub category: String,
    pub items: Vec<NurseryTeachItem>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NurseryCurriculum {
    pub version: u32,
    pub playlists: Vec<NurseryPlaylist>,
    pub tracks: Vec<NurseryTrack>,
}

pub const NURSERY_CATALOG_JSON: &str = include_str!("../assets/nursery_catalog.json");

pub fn builtin_curriculum() -> NurseryCurriculum {
    serde_json::from_str(NURSERY_CATALOG_JSON).expect("bundled nursery catalog is valid")
}

impl NurseryCurriculum {
    /// Diverse teach cards: rotate across tracks so a session mixes alphabet, numbers,
    /// animals… `seed` (e.g. day-of-year) makes the choice stable within a day.
    pub fn pick_teach_items(&self, count: usize, seed: u64) -> Vec<NurseryTeachItem> {
        let mut out = Vec::new();
        let mut offsets: Vec<usize> = self
            .tracks
            .iter()
            .map(|t| if t.items.is_empty() { 0 } else { (seed as usize + t.id.len()) % t.items.len() })
            .collect();
        while out.len() < count {
            let mut progressed = false;
            for (i, track) in self.tracks.iter().enumerate() {
                if out.len() >= count {
                    break;
                }
                if track.items.is_empty() {
                    continue;
                }
                out.push(track.items[offsets[i] % track.items.len()].clone());
                offsets[i] += 1;
                progressed = true;
            }
            if !progressed {
                break;
            }
        }
        out
    }

    /// Pick a video: prefer the requested playlist unless it is `all`/the default
    /// (then the whole curriculum), skip `recent` ids, avoid repeating the last category.
    pub fn pick_video(
        &self,
        preferred_playlist_id: &str,
        recent: &[String],
        last_category: Option<&str>,
        seed: u64,
    ) -> Option<(NurseryVideo, String)> {
        let diverse = preferred_playlist_id.is_empty() || preferred_playlist_id == "all" || preferred_playlist_id == "abc_phonics";
        let pool: Vec<(NurseryVideo, String)> = if diverse {
            self.playlists.iter().flat_map(|p| p.videos.iter().map(|v| (v.clone(), p.category.clone()))).collect()
        } else if let Some(p) = self.playlists.iter().find(|p| p.id == preferred_playlist_id).filter(|p| !p.videos.is_empty()) {
            p.videos.iter().map(|v| (v.clone(), p.category.clone())).collect()
        } else {
            self.playlists.iter().flat_map(|p| p.videos.iter().map(|v| (v.clone(), p.category.clone()))).collect()
        };
        if pool.is_empty() {
            return None;
        }
        let fresh: Vec<_> = pool.iter().filter(|(v, _)| !recent.contains(&v.video_id)).cloned().collect();
        let pool = if fresh.is_empty() { pool } else { fresh };
        let varied: Vec<_> = pool.iter().filter(|(_, c)| Some(c.as_str()) != last_category).cloned().collect();
        let pool = if varied.is_empty() { pool } else { varied };
        Some(pool[(seed as usize) % pool.len()].clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_matches_android_counts() {
        let c = builtin_curriculum();
        assert_eq!(c.playlists.len(), 5);
        assert_eq!(c.playlists.iter().map(|p| p.videos.len()).sum::<usize>(), 16);
        assert_eq!(c.tracks.len(), 5);
        assert_eq!(c.tracks.iter().map(|t| t.items.len()).sum::<usize>(), 52);
        assert!(c.tracks.iter().flat_map(|t| &t.items).all(|i| !i.speech_text.is_empty()));
    }

    #[test]
    fn teach_items_are_diverse() {
        let c = builtin_curriculum();
        let items = c.pick_teach_items(3, 7);
        assert_eq!(items.len(), 3);
        let ids: std::collections::HashSet<&str> = items.iter().map(|i| i.id.as_str()).collect();
        assert_eq!(ids.len(), 3);
    }

    #[test]
    fn video_picking_skips_recent_and_repeats_category() {
        let c = builtin_curriculum();
        let first = c.pick_video("all", &[], None, 0).unwrap();
        let next = c.pick_video("all", &[first.0.video_id.clone()], Some(&first.1), 0).unwrap();
        assert_ne!(next.0.video_id, first.0.video_id);
        assert_ne!(next.1, first.1);
        let p = c.pick_video("counting_123", &[], None, 0).unwrap();
        assert_eq!(p.1, "numbers");
    }
}
