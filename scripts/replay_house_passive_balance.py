#!/usr/bin/env python3
"""Simulate Lazax delegation passive balance from a copied tibot.db.

The script uses replay contests whose candidate initial snapshot has the new
top-level "context" payload key. It randomly assigns leaderboard users evenly
across Naalu, Mentak, and Hacan, then scores only the configured passive points:

- Naalu Gift of Prophecy: configurable points per correct prediction by a Naalu member.
- Mentak Pillage: configurable points per incorrect prediction by a non-Mentak member.
- Hacan Insider Trading: side-bet cost refunded per side bet by a Hacan member, or
  configurable points per Hacan prediction when --hacan-prediction-points is set.
"""

from __future__ import annotations

import argparse
import json
import random
import sqlite3
import statistics
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


HOUSES = ("NAALU", "MENTAK", "HACAN")


@dataclass(frozen=True)
class LeaderboardUser:
    user_id: str
    user_name: str
    total_points: int
    prediction_count: int
    correct_predictions: int


@dataclass(frozen=True)
class Contest:
    contest_id: int
    predictions: tuple[tuple[str, bool], ...]
    side_bet_user_ids: tuple[str, ...]


def main() -> None:
    args = parse_args()
    rng = random.Random(args.seed)

    with sqlite3.connect(expand_path(args.db)) as connection:
        connection.row_factory = sqlite3.Row
        users = load_leaderboard_users(connection, args.min_predictions)
        contests = load_context_contests(connection)

    if not users:
        raise SystemExit("No leaderboard users found after filters.")
    if not contests:
        raise SystemExit('No scored contests found with a top-level "context" payload key.')

    trial_results = []
    for _ in range(args.trials):
        assignments = random_house_assignments(users, rng)
        trial_results.append(score_passives(contests, assignments, args))

    print_summary(users, contests, trial_results, args)

    if args.show_sample:
        assignments = random_house_assignments(users, rng)
        sample = score_passives(contests, assignments, args)
        print("\nSample assignment result:")
        for house in HOUSES:
            member_count = sum(1 for assigned_house in assignments.values() if assigned_house == house)
            print(f"  {house:6} members={member_count:3d} passive_points={sample[house]:5d}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Simulate replay delegation passive balance.")
    parser.add_argument(
        "--db",
        default="~/Downloads/tibot.db",
        help="Path to copied SQLite DB. Default: ~/Downloads/tibot.db",
    )
    parser.add_argument("--trials", type=int, default=5000, help="Random assignments to simulate.")
    parser.add_argument("--seed", type=int, default=20260430, help="Random seed for reproducible output.")
    parser.add_argument(
        "--side-bet-cost",
        type=int,
        default=1,
        help="Current side-bet cost; Hacan passive refunds this amount per Hacan side bet.",
    )
    parser.add_argument(
        "--naalu-correct-points",
        type=int,
        default=2,
        help="Naalu passive points per correct prediction by a Naalu member.",
    )
    parser.add_argument(
        "--mentak-incorrect-points",
        type=int,
        default=1,
        help="Mentak passive points per incorrect prediction by a non-Mentak member.",
    )
    parser.add_argument(
        "--hacan-prediction-points",
        type=int,
        default=0,
        help="If greater than 0, score Hacan this many points per Hacan prediction instead of side-bet refunds.",
    )
    parser.add_argument(
        "--min-predictions",
        type=int,
        default=0,
        help="Only assign leaderboard users with at least this many predictions.",
    )
    parser.add_argument("--show-sample", action="store_true", help="Print one sample assignment result.")
    return parser.parse_args()


def expand_path(path: str) -> str:
    return str(Path(path).expanduser())


def load_leaderboard_users(connection: sqlite3.Connection, min_predictions: int) -> list[LeaderboardUser]:
    rows = connection.execute(
        """
        SELECT discord_user_id, discord_user_name, total_points, prediction_count, correct_predictions
        FROM combat_replay_leaderboard_entry
        WHERE prediction_count >= ?
        ORDER BY total_points DESC, discord_user_name COLLATE NOCASE ASC
        """,
        (min_predictions,),
    ).fetchall()
    return [
        LeaderboardUser(
            user_id=str(row["discord_user_id"]),
            user_name=row["discord_user_name"],
            total_points=int(row["total_points"] or 0),
            prediction_count=int(row["prediction_count"] or 0),
            correct_predictions=int(row["correct_predictions"] or 0),
        )
        for row in rows
    ]


def load_context_contests(connection: sqlite3.Connection) -> list[Contest]:
    rows = connection.execute(
        """
        SELECT
            c.id AS contest_id,
            cc.winner_faction,
            cc.attacker_faction,
            cc.initial_render_snapshot_json,
            rp.attacker_predictions_json,
            rp.defender_predictions_json
        FROM combat_contest c
        JOIN combat_candidate cc ON cc.id = c.candidate_id
        LEFT JOIN combat_replay_prediction rp ON rp.contest_id = c.id
        ORDER BY c.id ASC
        """
    ).fetchall()

    contests: list[Contest] = []
    for row in rows:
        if not has_context_payload(row["initial_render_snapshot_json"]):
            continue
        winner = normalized(row["winner_faction"])
        attacker = normalized(row["attacker_faction"])
        attacker_correct = bool(winner and attacker and winner == attacker)
        defender_correct = bool(winner and attacker and winner != attacker)

        predictions: list[tuple[str, bool]] = []
        predictions.extend((user_id, attacker_correct) for user_id in prediction_user_ids(row["attacker_predictions_json"]))
        predictions.extend((user_id, defender_correct) for user_id in prediction_user_ids(row["defender_predictions_json"]))

        side_bet_user_ids = tuple(
            str(side_bet_row["discord_user_id"])
            for side_bet_row in connection.execute(
                "SELECT discord_user_id FROM combat_contest_side_bets WHERE contest_id = ?",
                (row["contest_id"],),
            )
        )
        contests.append(Contest(int(row["contest_id"]), tuple(predictions), side_bet_user_ids))
    return contests


def has_context_payload(raw_json: str | None) -> bool:
    if not raw_json:
        return False
    try:
        payload = json.loads(raw_json)
    except json.JSONDecodeError:
        return False
    return isinstance(payload, dict) and isinstance(payload.get("context"), dict)


def prediction_user_ids(raw_json: str | None) -> Iterable[str]:
    if not raw_json:
        return ()
    try:
        predictions = json.loads(raw_json)
    except json.JSONDecodeError:
        return ()
    if not isinstance(predictions, list):
        return ()
    return (
        str(prediction["discordUserId"])
        for prediction in predictions
        if isinstance(prediction, dict) and prediction.get("discordUserId") is not None
    )


def normalized(value: str | None) -> str | None:
    return value.lower() if isinstance(value, str) else None


def random_house_assignments(users: list[LeaderboardUser], rng: random.Random) -> dict[str, str]:
    shuffled_users = users[:]
    rng.shuffle(shuffled_users)

    shuffled_houses = list(HOUSES)
    rng.shuffle(shuffled_houses)

    assignments: dict[str, str] = {}
    for index, user in enumerate(shuffled_users):
        assignments[user.user_id] = shuffled_houses[index % len(shuffled_houses)]
    return assignments


def score_passives(
    contests: list[Contest],
    assignments: dict[str, str],
    args: argparse.Namespace,
) -> dict[str, int]:
    scores = {house: 0 for house in HOUSES}
    for contest in contests:
        for user_id, correct in contest.predictions:
            house = assignments.get(user_id)
            if house is None:
                continue
            if correct and house == "NAALU":
                scores["NAALU"] += args.naalu_correct_points
            elif not correct and house != "MENTAK":
                scores["MENTAK"] += args.mentak_incorrect_points
            if house == "HACAN" and args.hacan_prediction_points > 0:
                scores["HACAN"] += args.hacan_prediction_points

        if args.hacan_prediction_points <= 0:
            for user_id in contest.side_bet_user_ids:
                if assignments.get(user_id) == "HACAN":
                    scores["HACAN"] += args.side_bet_cost
    return scores


def print_summary(
    users: list[LeaderboardUser],
    contests: list[Contest],
    trial_results: list[dict[str, int]],
    args: argparse.Namespace,
) -> None:
    prediction_count = sum(len(contest.predictions) for contest in contests)
    side_bet_count = sum(len(contest.side_bet_user_ids) for contest in contests)

    print("Replay House Passive Balance Simulation")
    print(f"  DB: {expand_path(args.db)}")
    print(f"  leaderboard users assigned: {len(users)}")
    print(f"  context-format contests: {len(contests)}")
    print(f"  predictions in sample: {prediction_count}")
    print(f"  side bets in sample: {side_bet_count}")
    print(f"  trials: {args.trials}")
    print(f"  seed: {args.seed}")
    print(f"  side bet cost/refund: {args.side_bet_cost}")
    print(f"  Naalu correct-prediction passive: {args.naalu_correct_points}")
    print(f"  Mentak non-Mentak incorrect-prediction passive: {args.mentak_incorrect_points}")
    if args.hacan_prediction_points > 0:
        print(f"  Hacan prediction passive: {args.hacan_prediction_points}")
    else:
        print("  Hacan prediction passive: disabled; using side-bet refunds")
    print()
    print("Passive points by house over random assignments:")
    print("  house     avg     stdev     min     p10     p50     p90     max   avg/contest")
    for house in HOUSES:
        values = [result[house] for result in trial_results]
        avg = statistics.fmean(values)
        stdev = statistics.pstdev(values)
        print(
            f"  {house:6} "
            f"{avg:7.1f} "
            f"{stdev:9.1f} "
            f"{min(values):7d} "
            f"{percentile(values, 0.10):7.0f} "
            f"{percentile(values, 0.50):7.0f} "
            f"{percentile(values, 0.90):7.0f} "
            f"{max(values):7d} "
            f"{avg / len(contests):11.2f}"
        )

    winners = {house: 0 for house in HOUSES}
    for result in trial_results:
        best = max(HOUSES, key=lambda house: result[house])
        winners[best] += 1
    print()
    print("Highest passive total frequency:")
    for house in HOUSES:
        print(f"  {house:6} {winners[house] / len(trial_results):6.1%}")


def percentile(values: list[int], percentile_value: float) -> float:
    if not values:
        return 0.0
    sorted_values = sorted(values)
    index = percentile_value * (len(sorted_values) - 1)
    lower = int(index)
    upper = min(lower + 1, len(sorted_values) - 1)
    fraction = index - lower
    return sorted_values[lower] * (1 - fraction) + sorted_values[upper] * fraction


if __name__ == "__main__":
    main()
