import pytest

from tfhub_ml.data.confirmation import load_confirmed_outdoor_ids


@pytest.mark.parametrize("contents", [
    "result_id\n1\n1\n",
    "result_id\n0\n",
    "result_id\n1.5\n",
    "result_id,other\n1,x\n",
    "result_id\n",
])
def test_invalid_confirmation_files_are_rejected(tmp_path, contents) -> None:
    path = tmp_path / "ids.csv"
    path.write_text(contents, encoding="utf-8")
    with pytest.raises(ValueError):
        load_confirmed_outdoor_ids(path)


def test_confirmation_file_is_read_as_exact_ids(tmp_path) -> None:
    path = tmp_path / "ids.csv"
    path.write_text("result_id\n1\n23\n", encoding="utf-8")
    assert load_confirmed_outdoor_ids(path) == frozenset({1, 23})
