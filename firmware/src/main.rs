use serde::{Serialize, Deserialize};
use serde_json::to_string;

#[derive(Serialize, Deserialize, Debug)]
pub struct ForecastEntry {
    pub consumption_average_power_interval: i32
}

fn main() {

    let entry = ForecastEntry { consumption_average_power_interval: 32};
    let str = serde_json::to_string(&entry).expect("Unable to serialize");

    println!("Hello world {:?}", str)
}