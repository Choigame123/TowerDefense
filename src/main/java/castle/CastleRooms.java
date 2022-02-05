package castle;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Interval;
import castle.components.Bundle;
import castle.components.CastleIcons;
import castle.components.PlayerData;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Iconc;
import mindustry.gen.Nulls;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.Tiles;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.units.RepairPoint;

import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class CastleRooms {

    public static final Seq<Room> rooms = new Seq<>();

    public static final int size = 8;

    public static Tile shardedSpawn, blueSpawn;

    public static class Room {
        public int x;
        public int y;
        public int centrex;
        public int centrey;
        public int endx;
        public int endy;

        public int cost;
        public int size;

        public String label;
        public boolean showLabel;

        public Room(int x, int y, int cost, int size) {
            this.x = x;
            this.y = y;
            this.centrex = x + size / 2;
            this.centrey = y + size / 2;
            this.endx = x + size;
            this.endy = y + size;

            this.cost = cost;
            this.size = size;

            this.label = "";
            this.showLabel = true;

            rooms.add(this);
        }

        public void update() {}

        public void buy(PlayerData data) {
            data.money -= cost;
        }

        public boolean canBuy(PlayerData data) {
            return data.money >= cost;
        }

        public boolean check(float x, float y) {
            return x > this.x * tilesize && y > this.y * tilesize && x < this.endx * tilesize && y < this.endy * tilesize;
        }

        public void spawn(Tiles tiles) {
            for (int x = 0; x <= size; x++) {
                for (int y = 0; y <= size; y++) {
                    Floor floor = (x == 0 || y == 0 || x == size || y == size ? Blocks.metalFloor5 : Blocks.metalFloor).asFloor();
                    Tile tile = tiles.getc(this.x + x, this.y + y);
                    if (tile != null) {
                        tile.setFloor(floor);
                    }
                }
            }
        }
    }



    public static class BlockRoom extends Room {
        public Block block;
        public Team team;

        public boolean bought;

        public BlockRoom(Block block, Team team, int x, int y, int cost, int size) {
            super(x, y, cost, size);
            this.block = block;
            this.team = team;

            this.bought = false;

            this.label = CastleIcons.get(block) + " :[white] " + cost;
        }

        public BlockRoom(Block block, Team team, int x, int y, int cost) {
            this(block, team, x, y, cost, block.size + 1);
        }

        @Override
        public void buy(PlayerData data) {
            super.buy(data);

            world.tile(centrex, centrey).setNet(block, team, 0);
            if (block instanceof ItemTurret turret) {
                world.tile(x, centrey).setNet(Blocks.itemSource, team, 0);
                world.build(x, centrey).configure(turret.ammoTypes.keys().toSeq().random());
            } else if (block instanceof LiquidTurret turret) {
                world.tile(x, centrey).setNet(Blocks.liquidSource, team, 0);
                world.build(x, centrey).configure(turret.ammoTypes.keys().toSeq().random());
            } else if (block instanceof LaserTurret || block instanceof RepairPoint) {
                world.tile(x, centrey).setNet(Blocks.liquidSource, team, 0);
                world.build(x, centrey).configure(Liquids.cryofluid);
            }

            bought = true;
            showLabel = false;
            Groups.player.each(p -> Call.label(p.con, Bundle.format("events.buy", Bundle.findLocale(p), data.player.coloredName()), 4f, centrex * tilesize, y * tilesize));
        }

        @Override
        public boolean canBuy(PlayerData data) {
            return super.canBuy(data) && !bought && world.build(centrex, centrey) == null;
        }

        @Override
        public void update() {
            if (bought && world.build(centrex, centrey) == null) {
                bought = false;
                showLabel = true;
            }
        }
    }



    public static class MinerRoom extends BlockRoom {
        public ItemStack stack;
        public Interval interval;

        public MinerRoom(ItemStack stack, Team team, int x, int y, int cost) {
            super(Blocks.laserDrill, team, x, y, cost);

            this.stack = stack;
            this.interval = new Interval();

            this.label = CastleIcons.get(block) + " (" + CastleIcons.get(stack.item) + ") :[white] " + cost;
        }

        @Override
        public void update() {
            super.update();

            if (bought && interval.get(300f)) {
                Call.transferItemTo(Nulls.unit, stack.item, stack.amount, centrex * tilesize, centrey * tilesize, team.core());
            }
        }
    }



    public static class CoreRoom extends BlockRoom {

        public CoreRoom(Team team, int x, int y, int cost) {
            super(Blocks.coreNucleus, team, x, y, cost, Blocks.coreShard.size + 1);
        }

        @Override
        public void update() {}

        @Override
        public boolean canBuy(PlayerData data) {
            return data.money >= cost && !bought;
        }

        @Override
        public void spawn(Tiles tiles) {
            for (int x = 0; x <= size; x++) {
                for (int y = 0; y <= size; y++) {
                    Tile tile = tiles.getc(this.x + x, this.y + y);
                    if (tile != null) {
                        tile.setFloor(Blocks.darkPanel4.asFloor());
                    }
                }
            }
        }
    }



    public static class UnitRoom extends Room {

        public enum UnitRoomType {
            attack, defend
        }

        public UnitType unitType;
        public UnitRoomType roomType;

        public int income;

        public UnitRoom(UnitType unitType, UnitRoomType roomType, int income, int x, int y, int cost) {
            super(x, y, cost, 4);
            this.unitType = unitType;
            this.roomType = roomType;
            this.income = income;

            StringBuilder str = new StringBuilder();

            str.append(" ".repeat(Math.max(0, (String.valueOf(income).length() + String.valueOf(cost).length() + 2) / 2))).append(CastleIcons.get(unitType));

            if (roomType == UnitRoomType.attack) str.append(" [accent]").append(Iconc.modeAttack);
            else str.append(" [scarlet]").append(Iconc.defense);

            str.append("\n[gray]").append(cost).append("\n[white]").append(Iconc.blockPlastaniumCompressor).append(" : ");

            this.label = str.append(income < 0 ? "[crimson]" : income > 0 ? "[lime]+" : "[gray]").append(income).toString();
        }

        @Override
        public void buy(PlayerData data) {
            super.buy(data);
            data.income += income;

            if (roomType == UnitRoomType.attack) {
                unitType.spawn(data.player.team(), (data.player.team() == Team.sharded ? blueSpawn.drawx() : shardedSpawn.drawx()) + Mathf.random(-40, 40), (data.player.team() == Team.sharded ? blueSpawn.drawy() : shardedSpawn.drawy()) + Mathf.random(-40, 40));
            } else if (data.player.team().core() != null) {
                unitType.spawn(data.player.team(), data.player.team().core().x + 30, data.player.team().core().y + Mathf.random(-40, 40));
            }
        }

        @Override
        public boolean canBuy(PlayerData data) {
            return super.canBuy(data) && (income > 0 || data.income - income >= 0);
        }
    }
}
